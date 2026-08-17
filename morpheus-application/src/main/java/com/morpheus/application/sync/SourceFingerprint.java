package com.morpheus.application.sync;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** Content fingerprint. M7 intentionally standardizes on SHA-256 bytes, never mtime-only fingerprints. */
public record SourceFingerprint(String sha256) implements Comparable<SourceFingerprint> {
    private static final String CHANGED_IDENTITY_MESSAGE =
            "source changed identity or metadata while fingerprint was being computed";
    private static final String CHANGED_SIZE_MESSAGE =
            "source changed size while fingerprint was being computed";

    public SourceFingerprint {
        Objects.requireNonNull(sha256, "sha256");
        sha256 = sha256.trim().toLowerCase(Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must contain exactly 64 hexadecimal characters");
        }
    }

    public static SourceFingerprint ofBytes(byte[] content) {
        Objects.requireNonNull(content, "content");
        return new SourceFingerprint(HexFormat.of().formatHex(digest().digest(content)));
    }

    public static SourceFingerprint ofFile(Path path) throws IOException {
        return ofFile(path, Long.MAX_VALUE);
    }

    /**
     * Hashes one regular path without following a final symbolic link and refuses to consume more than
     * {@code maxBytes}. The byte ceiling is enforced while reading. The path identity and metadata are also
     * observed before and after the descriptor-backed read. Provider file keys are compared as opaque values
     * when available; creation time supplies an additional portable replacement signal when file keys are absent.
     */
    public static SourceFingerprint ofFile(Path path, long maxBytes) throws IOException {
        return ofFile(path, maxBytes, ReadObserver.NONE);
    }

    static SourceFingerprint ofFile(Path path, long maxBytes, ReadObserver observer) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(observer, "observer");
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be non-negative");
        }

        BasicFileAttributes before = readAttributes(path);
        requireRegularNonSymbolic(before);
        if (before.size() > maxBytes) {
            throw new IOException("source file exceeded expected size while fingerprint was being computed");
        }

        MessageDigest digest = digest();
        long total = 0;
        boolean firstReadObserved = false;
        try (var channel = Files.newByteChannel(
                path,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)) {
            if (channel.size() != before.size()) {
                throw new IOException(CHANGED_SIZE_MESSAGE);
            }

            ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
            int read;
            while ((read = channel.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (read > maxBytes - total) {
                    throw new IOException("source file exceeded expected size while fingerprint was being computed");
                }
                if (read > before.size() - total) {
                    throw new IOException(CHANGED_SIZE_MESSAGE);
                }
                digest.update(buffer.array(), 0, read);
                total += read;
                buffer.clear();
                if (!firstReadObserved) {
                    firstReadObserved = true;
                    observer.observe(path, ReadCheckpoint.AFTER_FIRST_READ);
                }
            }

            if (total != before.size() || channel.size() != before.size()) {
                throw new IOException(CHANGED_SIZE_MESSAGE);
            }
        }

        observer.observe(path, ReadCheckpoint.BEFORE_FINAL_ATTRIBUTES);
        BasicFileAttributes after = readAttributes(path);
        if (!sameFileIdentity(before, after)) {
            throw new IOException(CHANGED_IDENTITY_MESSAGE);
        }

        return new SourceFingerprint(HexFormat.of().formatHex(digest.digest()));
    }

    static BasicFileAttributes readAttributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    static boolean sameFileIdentity(BasicFileAttributes before, BasicFileAttributes after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        if (!before.isRegularFile() || before.isSymbolicLink()
                || !after.isRegularFile() || after.isSymbolicLink()) {
            return false;
        }
        if (before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !before.creationTime().equals(after.creationTime())) {
            return false;
        }

        Object beforeKey = before.fileKey();
        Object afterKey = after.fileKey();
        if (beforeKey == null && afterKey == null) {
            return true;
        }
        return Objects.equals(beforeKey, afterKey);
    }

    private static void requireRegularNonSymbolic(BasicFileAttributes attributes) throws IOException {
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IOException("source path is not a regular non-symbolic file");
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    @Override
    public int compareTo(SourceFingerprint other) {
        return sha256.compareTo(other.sha256);
    }

    @Override
    public String toString() {
        return "sha256:" + sha256;
    }

    enum ReadCheckpoint {
        AFTER_FIRST_READ,
        BEFORE_FINAL_ATTRIBUTES
    }

    @FunctionalInterface
    interface ReadObserver {
        ReadObserver NONE = (path, checkpoint) -> { };

        void observe(Path path, ReadCheckpoint checkpoint) throws IOException;
    }
}
