package com.morpheus.application.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** Fail-closed SHA-256 verification primitive for explicitly trusted external executable JARs. */
public final class ExternalJarIntegrity {
    public static final int SHA256_HEX_LENGTH = 64;

    private ExternalJarIntegrity() {
    }

    public static Path verifySha256(Path jar, String expectedSha256) {
        Objects.requireNonNull(jar, "jar");
        String expected = normalizeSha256(expectedSha256);
        Path candidate = requireRegularJar(jar);
        String actual = sha256(candidate);
        if (!MessageDigest.isEqual(
                HexFormat.of().parseHex(expected),
                HexFormat.of().parseHex(actual))) {
            throw new IllegalArgumentException(
                    "external JAR integrity mismatch for " + candidate.getFileName()
                            + ": expected=" + expected + " actual=" + actual);
        }
        return candidate;
    }

    /**
     * Copies a trusted JAR to an owner-hardened private staging file and verifies that immutable copy.
     * The caller must delete the returned path after its classloader has been closed.
     */
    public static Path stageVerifiedCopy(Path jar, String expectedSha256) {
        String expected = normalizeSha256(expectedSha256);
        Path candidate = requireRegularJar(jar);
        Path staged = null;
        boolean success = false;
        try {
            staged = Files.createTempFile("morpheus-trusted-plugin-" + expected.substring(0, 12) + "-", ".jar");
            try (InputStream input = Files.newInputStream(candidate, LinkOption.NOFOLLOW_LINKS)) {
                Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING);
            }
            new LocalWritePermissionHardener().hardenFile(staged);
            verifySha256(staged, expected);
            success = true;
            return staged;
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot stage trusted external JAR", failure);
        } finally {
            if (!success && staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException ignored) {
                    // Preserve the primary integrity/staging failure.
                }
            }
        }
    }

    public static String sha256(Path jar) {
        Objects.requireNonNull(jar, "jar");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(jar, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 must be available", failure);
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot hash external JAR", failure);
        }
    }

    public static String normalizeSha256(String value) {
        Objects.requireNonNull(value, "expectedSha256");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{" + SHA256_HEX_LENGTH + "}")) {
            throw new IllegalArgumentException("expected SHA-256 must contain exactly 64 hexadecimal characters");
        }
        return normalized;
    }

    private static Path requireRegularJar(Path jar) {
        Path candidate = Objects.requireNonNull(jar, "jar").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(candidate) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("trusted external JAR must be a regular non-symbolic file");
        }
        return candidate;
    }
}
