package com.morpheus.application.foundation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fixed vectors: every source fingerprint, excerpt digest, schema checksum and lifecycle fingerprint of the product is
 * this function, so a vector that moves is a re-ingestion of every project.
 */
class TextDigestTest {
    @Test
    void theEmptyTextHasTheDigestOfNoBytes() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", TextDigest.sha256(""));
    }

    @Test
    void anAsciiTextHasTheDigestOfItsBytes() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TextDigest.sha256("abc"));
    }

    @Test
    void aTextOutsideAsciiIsDigestedAsUtf8() {
        assertEquals("c499e13f5d9c0761c79e96fe72e761e914746da7a203c691ee1a4d717a43c12e",
                TextDigest.sha256("Évaluer naïve — 日本語 Ωmega"));
    }

    @Test
    void anAbsentTextIsRefusedByName() {
        NullPointerException refused = assertThrows(NullPointerException.class, () -> TextDigest.sha256(null));
        assertEquals("text", refused.getMessage());
    }

    @Test
    void thePlatformWithoutTheDigestIsReportedWithOneWording() {
        assertEquals("SHA-256 must be available", TextDigest.UNAVAILABLE);
    }
}
