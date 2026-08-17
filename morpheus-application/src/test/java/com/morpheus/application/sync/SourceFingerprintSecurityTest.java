package com.morpheus.application.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceFingerprintSecurityTest {

    @TempDir
    Path tempDir;

    @Test
    void boundedFingerprintAcceptsExactlyTheObservedBytes() throws Exception {
        Path source = tempDir.resolve("source.md");
        byte[] content = "bounded-source".getBytes(StandardCharsets.UTF_8);
        Files.write(source, content);

        assertEquals(SourceFingerprint.ofBytes(content), SourceFingerprint.ofFile(source, content.length));
    }

    @Test
    void boundedFingerprintRejectsContentBeyondTheObservedSize() throws Exception {
        Path source = tempDir.resolve("grown.md");
        Files.writeString(source, "12345", StandardCharsets.UTF_8);

        IOException failure = assertThrows(IOException.class, () -> SourceFingerprint.ofFile(source, 4));

        assertTrue(failure.getMessage().contains("exceeded expected size"));
    }

    @Test
    void boundedFingerprintDoesNotFollowFinalSymbolicLinkWhenSupported() throws Exception {
        Path target = tempDir.resolve("outside.md");
        Files.writeString(target, "outside", StandardCharsets.UTF_8);
        Path link = tempDir.resolve("source-link.md");
        if (!tryCreateSymbolicLink(link, target)) {
            return;
        }

        assertThrows(IOException.class, () -> SourceFingerprint.ofFile(link, Files.size(target)));
    }

    private boolean tryCreateSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return false;
        }
    }
}
