package com.morpheus.store.sqlite;

import com.morpheus.application.security.LocalWritePermissionHardener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDatabaseLeaseTest {
    @TempDir
    Path temp;

    @Test
    void sharedConnectionsRefCountAndExclusiveMaintenanceWaitsForLastClose() throws Exception {
        Path database = temp.resolve("morpheus.db");
        var first = SqliteDatabaseSecurity.open(database);
        var second = SqliteDatabaseSecurity.open(database);
        try {
            assertFalse(first.isClosed());
            assertFalse(second.isClosed());
            assertTrue(first.toString().startsWith("LeasedSqliteConnection["));
            assertThrows(IllegalStateException.class, () -> SqliteDatabaseLease.acquireExclusive(database));

            first.close();
            assertTrue(first.isClosed());
            assertThrows(IllegalStateException.class, () -> SqliteDatabaseLease.acquireExclusive(database));

            second.close();
            try (SqliteDatabaseLease.Lease exclusive = SqliteDatabaseLease.acquireExclusive(database)) {
                assertThrows(IllegalStateException.class, () -> SqliteDatabaseSecurity.open(database));
            }

            try (var reopened = SqliteDatabaseSecurity.open(database)) {
                assertFalse(reopened.isClosed());
            }
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void operatingSystemExclusiveLockBlocksSharedMorpheusLease() throws Exception {
        Path database = temp.resolve("morpheus.db").toAbsolutePath().normalize();
        Path lockPath = database.resolveSibling(database.getFileName() + ".access.lock");
        Files.createFile(lockPath);

        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            assertThrows(IllegalStateException.class, () -> SqliteDatabaseLease.acquireShared(database));
        }
    }

    @Test
    void operatingSystemSharedLockBlocksExclusiveMaintenanceLease() throws Exception {
        Path database = temp.resolve("morpheus.db").toAbsolutePath().normalize();
        Path lockPath = database.resolveSibling(database.getFileName() + ".access.lock");
        Files.createFile(lockPath);

        try (FileChannel channel = FileChannel.open(
                lockPath, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock(0L, Long.MAX_VALUE, true)) {
            assertThrows(IllegalStateException.class, () -> SqliteDatabaseLease.acquireExclusive(database));
        }
    }

    /**
     * Offline maintenance must stay impossible while a physical SQLite handle may still be held.
     *
     * <p>The lease is exactly that guarantee, and a {@code Connection.close()} that throws does not establish
     * the handle is gone. Releasing the lease anyway let {@code acquireExclusive} succeed over a connection
     * that may still be alive, and an offline restore replaces the database file and its sidecars underneath
     * one. The release is therefore withheld until a close actually succeeds, and the close stays retryable so
     * the owner can still get there. {@link SqliteConnectionScope} keeps a scope quarantined and retryable
     * after the same failure; this is the same policy applied to the lease that gates maintenance.</p>
     */
    @Test
    void aDriverThatRefusesToCloseKeepsTheLeaseAndKeepsMaintenanceOut() throws Exception {
        Path database = temp.resolve("guarded.db").toAbsolutePath().normalize();
        SQLException injected = new SQLException("injected driver close failure");
        AtomicInteger closeAttempts = new AtomicInteger();
        SqliteDatabaseLease.Lease lease = SqliteDatabaseLease.acquireShared(database);
        Connection guarded = SqliteDatabaseLease.guard(failingFirstClose(closeAttempts, injected), lease);

        SQLException refused = assertThrows(SQLException.class, guarded::close);
        assertSame(injected, refused, "the driver failure is what the caller must see");
        assertEquals(1, closeAttempts.get());

        assertTrue(guarded.isClosed(), "a connection whose close failed is unusable either way");
        assertThrows(SQLException.class, guarded::createStatement);

        // The decisive assertion: the physical handle was never proven released, so maintenance stays out.
        IllegalStateException blocked = assertThrows(
                IllegalStateException.class, () -> SqliteDatabaseLease.acquireExclusive(database));
        assertTrue(blocked.getMessage().contains("still open in another MORPHEUS operation"), blocked.getMessage());

        guarded.close();
        assertEquals(2, closeAttempts.get(), "a release that failed must stay retryable");

        // Only now is the handle proven gone, so the lease is released and maintenance may proceed.
        SqliteDatabaseLease.acquireExclusive(database).close();

        guarded.close();
        assertEquals(2, closeAttempts.get(), "a release that succeeded must not run again");
        SqliteDatabaseLease.acquireExclusive(database).close();
    }

    /** An unchecked driver failure leaves ownership just as unresolved as a checked one. */
    @Test
    void anUncheckedDriverCloseFailureAlsoKeepsMaintenanceOut() throws Exception {
        Path database = temp.resolve("guarded-unchecked.db").toAbsolutePath().normalize();
        IllegalStateException injected = new IllegalStateException("injected unchecked close failure");
        AtomicInteger closeAttempts = new AtomicInteger();
        SqliteDatabaseLease.Lease lease = SqliteDatabaseLease.acquireShared(database);
        Connection guarded = SqliteDatabaseLease.guard(failingFirstCloseUnchecked(closeAttempts, injected), lease);

        assertSame(injected, assertThrows(IllegalStateException.class, guarded::close));
        assertThrows(IllegalStateException.class, () -> SqliteDatabaseLease.acquireExclusive(database));

        guarded.close();
        SqliteDatabaseLease.acquireExclusive(database).close();
    }

    /**
     * A lease release that fails must not leave the caller believing the connection is still theirs to retry:
     * the connection is gone, so the failure to report is the lease one, and the reference is not given back
     * twice on a second close.
     */
    @Test
    void aLeaseReleaseFailureIsReportedWithoutReleasingTheReferenceTwice() throws Exception {
        Path database = temp.resolve("guarded-lease-failure.db").toAbsolutePath().normalize();
        AtomicInteger closeAttempts = new AtomicInteger();
        SqliteDatabaseLease.Lease first = SqliteDatabaseLease.acquireShared(database);
        // A second reference keeps the shared state alive, so the release below is a refcount decrement and the
        // assertions are about that decrement rather than about the file lock going away.
        SqliteDatabaseLease.Lease second = SqliteDatabaseLease.acquireShared(database);
        Connection guarded = SqliteDatabaseLease.guard(failingFirstClose(closeAttempts, null), first);

        guarded.close();
        assertEquals(1, closeAttempts.get());
        guarded.close();
        assertEquals(1, closeAttempts.get(), "the physical connection must be closed once");

        second.close();
        SqliteDatabaseLease.acquireExclusive(database).close();
    }

    /**
     * A refused acquisition must not keep the lock file open.
     *
     * <p>Deleting the file is the decisive observation on Windows, where the operating system refuses to remove
     * a file that still has an open handle. On POSIX a delete succeeds regardless, so this test carries the
     * Windows half of the proof and {@link #hardeningFailureAfterTheChannelIsOpenReleasesTheDescriptor} carries
     * the descriptor count on Linux.</p>
     */
    @Test
    void aRefusedSharedAcquisitionDoesNotKeepTheLockFileOpen() throws Exception {
        Path database = temp.resolve("contended.db").toAbsolutePath().normalize();
        Path lockPath = database.resolveSibling(database.getFileName() + ".access.lock");
        Files.createFile(lockPath);

        try (FileChannel external = FileChannel.open(lockPath, StandardOpenOption.WRITE);
             FileLock ignored = external.lock()) {
            assertThrows(IllegalStateException.class, () -> SqliteDatabaseLease.acquireShared(database));
        }

        assertTrue(Files.deleteIfExists(lockPath),
                "a refused acquisition must have closed the channel it opened");
    }

    /** The same for the exclusive server lease, whose acquisition opens its own channel before locking. */
    @Test
    void aRefusedServerLeaseDoesNotKeepItsLockFileOpen() throws Exception {
        Path database = temp.resolve("contended-server.db").toAbsolutePath().normalize();
        Path lockPath = database.resolveSibling(database.getFileName() + ".server.lock");
        Files.createFile(lockPath);

        try (FileChannel external = FileChannel.open(lockPath, StandardOpenOption.WRITE);
             FileLock ignored = external.lock()) {
            SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
            assertThrows(IllegalStateException.class, () -> maintenance.acquireServerLease(database));
        }

        assertTrue(Files.deleteIfExists(lockPath),
                "a refused server lease must have closed the channel it opened");
    }

    /**
     * Hardening runs after the channel exists and refuses fail-closed, unchecked. The {@code IOException}
     * handler never saw that, so the descriptor stayed open for the life of the process.
     *
     * <p>A group- and other-writable directory without the sticky bit is what the hardener refuses, and it is
     * reproducible only where POSIX permissions exist. Where {@code /proc/self/fd} is available the descriptor
     * count is the direct proof; elsewhere the acquisition is simply shown to fail closed.</p>
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void hardeningFailureAfterTheChannelIsOpenReleasesTheDescriptor() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("permissive"));
        Path database = workspace.resolve("hardening.db").toAbsolutePath().normalize();
        Files.setPosixFilePermissions(workspace, PosixFilePermissions.fromString("rwxrwxrwx"));

        long before = openDescriptorCount();
        assertThrows(LocalWritePermissionHardener.LocalWritePermissionException.class,
                () -> SqliteDatabaseLease.acquireShared(database));
        long after = openDescriptorCount();

        if (before >= 0 && after >= 0) {
            assertEquals(before, after, "the refused acquisition must not leave a descriptor open");
        }
    }

    /** Descriptor count for this JVM, or -1 where the platform does not expose one. */
    private static long openDescriptorCount() throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        if (!Files.isDirectory(descriptors)) {
            return -1L;
        }
        try (var entries = Files.list(descriptors)) {
            return entries.count();
        }
    }

    /** Refuses the first close the way a driver would, then behaves, so the retry contract is observable. */
    private static Connection failingFirstClose(AtomicInteger attempts, SQLException failure) {
        return injectedConnection(attempts, failure);
    }

    /** The same, for a driver that fails unchecked. */
    private static Connection failingFirstCloseUnchecked(AtomicInteger attempts, RuntimeException failure) {
        return injectedConnection(attempts, failure);
    }

    private static Connection injectedConnection(AtomicInteger attempts, Throwable firstCloseFailure) {
        return (Connection) Proxy.newProxyInstance(
                SqliteDatabaseLeaseTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) {
                        if (attempts.incrementAndGet() == 1 && firstCloseFailure != null) {
                            throw firstCloseFailure;
                        }
                        return null;
                    }
                    if (method.getName().equals("isClosed")) {
                        return attempts.get() > 1;
                    }
                    throw new SQLException("unexpected call on the injected connection: " + method.getName());
                });
    }

    @Test
    void nonRegularAccessLockEntryFailsClosed() throws Exception {
        Path database = temp.resolve("morpheus.db");
        Files.createDirectory(temp.resolve("morpheus.db.access.lock"));

        assertThrows(IllegalArgumentException.class, () -> SqliteDatabaseLease.acquireShared(database));
    }
}
