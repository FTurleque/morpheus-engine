package com.morpheus.application.foundation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The SHA-256 of a text: the lower-case hexadecimal digest of its UTF-8 bytes. Source fingerprints, excerpt digests,
 * schema checksums and lifecycle fingerprints are this value, so every module computes it here and nowhere else.
 */
public final class TextDigest {
    /** The one wording of a platform that does not provide SHA-256, whichever module asked. */
    public static final String UNAVAILABLE = "SHA-256 must be available";

    private TextDigest() {
    }

    public static String sha256(String text) {
        Objects.requireNonNull(text, "text");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(UNAVAILABLE, exception);
        }
    }
}
