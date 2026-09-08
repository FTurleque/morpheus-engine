package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filesystem custody of the identity snapshot, exercised without any knowledge of what it carries.
 *
 * <p>The store's whole job is that a reader never observes a half-written snapshot, that neither a reader nor a
 * writer can be redirected through a link, and that two mutations never interleave. Each of those is a property of
 * the disk rather than of the format, so each is checked here against a real one.</p>
 */
class RemoteIdentityFileStoreTest {
    @TempDir
    Path temp;

    @Test
    void aWrittenSnapshotIsReadBackExactly() {
        Path file = temp.resolve("auth.txt");

        RemoteIdentityFileStore.writeAtomically(file, "first\nsecond\n");

        assertEquals(List.of("first", "second"), RemoteIdentityFileStore.readLines(file, "unused"));
        assertTrue(RemoteIdentityFileStore.exists(file));
    }

    @Test
    void writingReplacesTheWholeSnapshotRatherThanAppending() {
        Path file = temp.resolve("auth.txt");
        RemoteIdentityFileStore.writeAtomically(file, "old-and-longer\nsecond\n");

        RemoteIdentityFileStore.writeAtomically(file, "new\n");

        assertEquals(List.of("new"), RemoteIdentityFileStore.readLines(file, "unused"));
    }

    @Test
    void aFailedWriteLeavesNoTemporaryFileBehind() throws IOException {
        Path file = temp.resolve("auth.txt");

        assertThrows(IllegalArgumentException.class,
                () -> RemoteIdentityFileStore.writeAtomically(file, "x".repeat(RemoteIdentityFileStore.MAX_FILE_BYTES + 1)));

        try (var entries = Files.list(temp)) {
            assertTrue(entries.noneMatch(entry -> entry.getFileName().toString().startsWith(".morpheus-auth-")));
        }
        assertFalse(Files.exists(file), "a refused write must not create the file it refused to write");
    }

    @Test
    void contentAtTheSizeBoundaryIsAcceptedAndOneByteMoreIsRefused() {
        Path file = temp.resolve("auth.txt");

        RemoteIdentityFileStore.writeAtomically(file, "x".repeat(RemoteIdentityFileStore.MAX_FILE_BYTES));

        assertEquals(RemoteIdentityFileStore.MAX_FILE_BYTES,
                RemoteIdentityFileStore.readLines(file, "unused").get(0).length());
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> RemoteIdentityFileStore.writeAtomically(
                        file, "x".repeat(RemoteIdentityFileStore.MAX_FILE_BYTES + 1)))
                .getMessage().contains("would exceed " + RemoteIdentityFileStore.MAX_FILE_BYTES + " bytes"));
    }

    /** The bound is on bytes, not characters: a multibyte payload must not slip past it by counting short. */
    @Test
    void theSizeBoundIsCountedInBytesRatherThanCharacters() {
        Path file = temp.resolve("auth.txt");
        String multibyte = "é".repeat(RemoteIdentityFileStore.MAX_FILE_BYTES / 2 + 1);

        assertTrue(multibyte.length() <= RemoteIdentityFileStore.MAX_FILE_BYTES);
        assertThrows(IllegalArgumentException.class,
                () -> RemoteIdentityFileStore.writeAtomically(file, multibyte));
    }

    @Test
    void anOversizedFileOnDiskIsRefusedOnReadWithTheBoundInTheMessage() throws IOException {
        Path file = temp.resolve("auth.txt");
        Files.writeString(file, "x".repeat(RemoteIdentityFileStore.MAX_FILE_BYTES + 1), StandardCharsets.UTF_8);

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> RemoteIdentityFileStore.readLines(file, "cannot read"))
                .getMessage().contains("exceeds " + RemoteIdentityFileStore.MAX_FILE_BYTES + " bytes"));
    }

    @Test
    void aMissingOrNonRegularFileIsRefusedRatherThanReadAsEmpty() throws IOException {
        Path missing = temp.resolve("absent.txt");
        Path directory = Files.createDirectory(temp.resolve("a-directory"));

        assertFalse(RemoteIdentityFileStore.exists(missing));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> RemoteIdentityFileStore.readLines(missing, "cannot read"))
                .getMessage().contains("regular non-symbolic file"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> RemoteIdentityFileStore.secureExistingFile(directory))
                .getMessage().contains("regular non-symbolic file"));
    }

    @Test
    void aFilesystemRootHasNoParentAndIsRefused() {
        Path root = temp.getRoot();

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> RemoteIdentityFileStore.normalizedFile(root))
                .getMessage().contains("must have a parent directory"));
    }

    @Test
    void aSymbolicAuthFileIsRefusedForReadingAndForWriting() throws IOException {
        Path target = temp.resolve("real-auth.txt");
        Files.writeString(target, "content\n", StandardCharsets.UTF_8);
        Path link = temp.resolve("linked-auth.txt");
        if (!canCreateSymbolicLink(link, target)) return;

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> RemoteIdentityFileStore.readLines(link, "cannot read"))
                .getMessage().contains("must not be a symbolic link"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> RemoteIdentityFileStore.writeAtomically(link, "replaced\n"))
                .getMessage().contains("must not be a symbolic link"));
        assertEquals("content\n", Files.readString(target),
                "a refused write must not have reached the link target");
    }

    @Test
    void aSymbolicMutationLockIsRefusedBeforeAnyWorkRuns() throws IOException {
        Path file = temp.resolve("auth.txt");
        Path lockTarget = temp.resolve("lock-target");
        Files.writeString(lockTarget, "", StandardCharsets.UTF_8);
        Path lock = RemoteIdentityFileStore.mutationLockPath(file);
        if (!canCreateSymbolicLink(lock, lockTarget)) return;

        AtomicBoolean ran = new AtomicBoolean();

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> RemoteIdentityFileStore.withMutationLock(file, ignored -> {
                    ran.set(true);
                    return null;
                }))
                .getMessage().contains("must not be a symbolic link"));
        assertFalse(ran.get(), "the mutation must not run once its lock is untrustworthy");
    }

    @Test
    void theLockPathSitsBesideTheAuthFileRatherThanInsideIt() {
        Path file = temp.resolve("auth.txt");

        Path lock = RemoteIdentityFileStore.mutationLockPath(file);

        assertEquals(temp.toAbsolutePath().normalize(), lock.getParent());
        assertEquals("auth.txt.lock", lock.getFileName().toString());
    }

    @Test
    void twoConcurrentMutationsNeverInterleave() throws Exception {
        Path file = temp.resolve("auth.txt");
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger observedOverlap = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);

        List<Callable<Void>> tasks = new java.util.ArrayList<>();
        for (int worker = 0; worker < workers; worker++) {
            tasks.add(() -> {
                ready.countDown();
                ready.await(10, TimeUnit.SECONDS);
                RemoteIdentityFileStore.withMutationLock(file, held -> {
                    if (concurrent.incrementAndGet() > 1) observedOverlap.incrementAndGet();
                    int previous = RemoteIdentityFileStore.exists(held)
                            ? Integer.parseInt(RemoteIdentityFileStore.readLines(held, "unused").get(0))
                            : 0;
                    RemoteIdentityFileStore.writeAtomically(held, (previous + 1) + System.lineSeparator());
                    concurrent.decrementAndGet();
                    completed.incrementAndGet();
                    return null;
                });
                return null;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            for (Future<Void> result : pool.invokeAll(tasks, 60, TimeUnit.SECONDS)) {
                result.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, observedOverlap.get(), "the mutation lock must exclude concurrent writers");
        assertEquals(workers, completed.get());
        assertEquals(String.valueOf(workers), RemoteIdentityFileStore.readLines(file, "unused").get(0),
                "no increment may be lost to a concurrent write");
    }

    @Test
    void absentArgumentsAreRefusedRatherThanDefaulted() {
        assertThrows(NullPointerException.class, () -> RemoteIdentityFileStore.normalizedFile(null));
        assertThrows(NullPointerException.class, () -> RemoteIdentityFileStore.exists(null));
        assertThrows(NullPointerException.class,
                () -> RemoteIdentityFileStore.writeAtomically(temp.resolve("auth.txt"), null));
        assertThrows(NullPointerException.class,
                () -> RemoteIdentityFileStore.withMutationLock(temp.resolve("auth.txt"), null));
    }

    /** Windows without the developer-mode privilege cannot create one; the invariant is still asserted elsewhere. */
    private static boolean canCreateSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            return false;
        }
    }
}
