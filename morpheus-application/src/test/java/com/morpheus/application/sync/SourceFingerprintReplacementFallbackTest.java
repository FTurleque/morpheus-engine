package com.morpheus.application.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceFingerprintReplacementFallbackTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteAndRecreateWithSameSizeAndMtimeIsRejectedEvenWhenMetadataIdentityIsWeak() throws Exception {
        Path source = tempDir.resolve("source.md");
        Path replacement = tempDir.resolve("replacement.md");
        Files.writeString(source, "AAAA", StandardCharsets.UTF_8);
        Files.writeString(replacement, "BBBB", StandardCharsets.UTF_8);
        FileTime commonMtime = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(source, commonMtime);
        Files.setLastModifiedTime(replacement, commonMtime);
        assertEquals(Files.size(source), Files.size(replacement));
        assertEquals(Files.getLastModifiedTime(source), Files.getLastModifiedTime(replacement));

        IOException failure = assertThrows(IOException.class, () -> SourceFingerprint.ofFile(
                source,
                Files.size(source),
                (path, checkpoint) -> {
                    if (checkpoint == SourceFingerprint.ReadCheckpoint.AFTER_FIRST_READ) {
                        Files.delete(path);
                        Files.move(replacement, path, StandardCopyOption.REPLACE_EXISTING);
                    }
                }));

        assertTrue(failure.getMessage().contains("identity, metadata, or content"), failure::getMessage);
    }
}
