package com.morpheus.api;

import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.security.LocalWritePermissionHardener;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/**
 * Filesystem custody of the remote identity snapshot.
 *
 * <p>This component owns every interaction with the disk and nothing about the content it carries: it never parses
 * an identity, never sees a token, and never decides whether a mutation is allowed. What it guarantees is that a
 * read observes a regular owner-protected file reached through no symbolic link, that a write either replaces the
 * whole snapshot or leaves the previous one intact, and that two mutations -- in this JVM or in two cooperating
 * MORPHEUS processes -- never interleave.</p>
 */
final class RemoteIdentityFileStore {
    static final int MAX_FILE_BYTES = 256 * 1024;

    /** Serializes mutations inside this JVM; the sidecar lock serializes them across processes. */
    private static final Object MUTATION_LOCK = new Object();

    private RemoteIdentityFileStore() {
    }

    @FunctionalInterface
    interface MutationWork<T> {
        T run(Path file);
    }

    /**
     * Runs one mutation while holding both the in-JVM monitor and the inter-process sidecar lock.
     *
     * <p>The lock lives beside the identity file rather than in it, so acquiring it never has to open the file the
     * mutation is about to replace atomically.</p>
     */
    static <T> T withMutationLock(Path authFile, MutationWork<T> work) {
        Objects.requireNonNull(work, "work");
        synchronized (MUTATION_LOCK) {
            Path file = normalizedFile(authFile);
            Path parent = requireParent(file);
            Path lockFile = mutationLockPath(file);
            try {
                Files.createDirectories(parent);
                LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
                hardener.hardenDirectory(parent);
                rejectSymbolic(lockFile, "remote auth mutation lock");
                try (FileChannel channel = FileChannel.open(
                        lockFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS)) {
                    hardener.hardenFile(lockFile);
                    try (FileLock ignored = channel.lock()) {
                        return work.run(file);
                    }
                }
            } catch (IOException failure) {
                throw new IllegalArgumentException("cannot lock remote auth file for mutation", failure);
            }
        }
    }

    static Path mutationLockPath(Path authFile) {
        Path file = normalizedFile(authFile);
        return file.resolveSibling(file.getFileName() + ".lock");
    }

    /**
     * Reads the snapshot after revalidating the whole path it was reached through.
     *
     * <p>The parent chain is part of the file identity: a protected file can still be replaced when an ancestor is
     * writable, so the ancestor check is repeated for every security-sensitive read rather than trusted from
     * whatever hardened the directory earlier.</p>
     */
    static List<String> readLines(Path authFile, String failureMessage) {
        Objects.requireNonNull(failureMessage, "failureMessage");
        Path file = secureExistingFile(authFile);
        Path parent = requireParent(file);
        try {
            new LocalWritePermissionHardener().requireWriteProtectedDirectory(parent);
            String text = SafeWorkspaceFileResolver.rootedAt(parent)
                    .readUtf8(file.getFileName(), MAX_FILE_BYTES);
            return text.lines().toList();
        } catch (IOException | RuntimeException failure) {
            if (failure.getMessage() != null && failure.getMessage().contains("exceeds maximum input size")) {
                throw new IllegalArgumentException(
                        "remote auth file exceeds " + MAX_FILE_BYTES + " bytes", failure);
            }
            throw new IllegalArgumentException(failureMessage, failure);
        }
    }

    static boolean exists(Path file) {
        return Files.exists(Objects.requireNonNull(file, "file"), LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Replaces the snapshot as a single atomic move from a private temporary file.
     *
     * <p>Writing in place would expose a truncated identity file to a concurrent reader, and to a crash. The
     * temporary file is hardened before the move so the published file is never briefly readable by anyone but its
     * owner, and it is deleted whatever happens, so a failed write leaves no partially rendered snapshot behind.</p>
     */
    static void writeAtomically(Path file, String content) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(content, "content");
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("remote auth file would exceed " + MAX_FILE_BYTES + " bytes");
        }
        Path parent = requireParent(file);
        try {
            Files.createDirectories(parent);
            rejectSymbolic(file, "remote auth file");
            Path temp = Files.createTempFile(parent, ".morpheus-auth-", ".tmp");
            try {
                Files.writeString(temp, content, StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
                hardener.hardenDirectory(parent);
                hardener.hardenFile(temp);
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                hardener.hardenFile(file);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot update remote auth file", failure);
        }
    }

    static Path normalizedFile(Path authFile) {
        Objects.requireNonNull(authFile, "authFile");
        Path file = authFile.toAbsolutePath().normalize();
        requireParent(file);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) rejectSymbolic(file, "remote auth file");
        return file;
    }

    static Path secureExistingFile(Path authFile) {
        Path file = normalizedFile(authFile);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException("remote auth file must be a regular non-symbolic file");
        }
        return file;
    }

    static void rejectSymbolic(Path path, String label) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException(label + " must not be a symbolic link");
        }
    }

    private static Path requireParent(Path file) {
        Path parent = file.getParent();
        if (parent == null) throw new IllegalArgumentException("remote auth file must have a parent directory");
        return parent;
    }
}
