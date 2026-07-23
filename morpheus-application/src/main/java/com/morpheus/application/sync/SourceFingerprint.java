package com.morpheus.application.sync;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Objects.requireNonNull(path, "path");
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
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
