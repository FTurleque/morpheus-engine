package com.morpheus.application.sync;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** Content fingerprint. M7 intentionally standardizes on SHA-256 bytes, never mtime-only fingerprints. */
public record SourceFingerprint(String sha256) implements Comparable<SourceFingerprint> {
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
     * {@code maxBytes}. The byte ceiling is enforced while reading, so a file that grows or is replaced after
     * an earlier filesystem inspection cannot turn a bounded scan into an unbounded read.
     */
    public static SourceFingerprint ofFile(Path path, long maxBytes) throws IOException {
        Objects.requireNonNull(path, "path");
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be non-negative");
        }
        MessageDigest digest = digest();
        long total = 0;
        try (var channel = Files.newByteChannel(
                path,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
            int read;
            while ((read = channel.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (read > maxBytes - total) {
                    throw new IOException("source file exceeded expected size while fingerprint was being computed");
                }
                digest.update(buffer.array(), 0, read);
                total += read;
                buffer.clear();
            }
        }
        return new SourceFingerprint(HexFormat.of().formatHex(digest.digest()));
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
}
