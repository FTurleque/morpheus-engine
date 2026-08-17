package com.morpheus.application.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

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
    void stableFingerprintKeepsTheEstablishedSha256Value() throws Exception {
        Path source = tempDir.resolve("stable.md");
        Files.writeString(source, "bounded-source", StandardCharsets.UTF_8);

        SourceFingerprint fingerprint = SourceFingerprint.ofFile(source);

        assertEquals(
                "1d50e670ee92d0a043c35cb9fd4849a74f7b2d20c67ebb14503ddca4ff1bf878",
                fingerprint.sha256());
    }

    @Test
    void boundedFingerprintRejectsContentBeyondTheObservedSize() throws Exception {
        Path source = tempDir.resolve("grown.md");
        Files.writeString(source, "12345", StandardCharsets.UTF_8);

        IOException failure = assertThrows(IOException.class, () -> SourceFingerprint.ofFile(source, 4));

        assertTrue(failure.getMessage().contains("exceeded expected size"));
    }

    @Test
    void fileGrowthDuringReadCannotExceedTheReservedBudget() throws Exception {
        Path source = tempDir.resolve("grows-during-read.bin");
        byte[] initial = new byte[32 * 1024];
        Arrays.fill(initial, (byte) 'a');
        Files.write(source, initial);

        IOException failure = assertThrows(IOException.class, () -> SourceFingerprint.ofFile(
                source,
                initial.length,
                (path, checkpoint) -> {
                    if (checkpoint == SourceFingerprint.ReadCheckpoint.AFTER_FIRST_READ) {
                        Files.write(path, new byte[]{'b'}, StandardOpenOption.APPEND);
                    }
                }));

        assertTrue(failure.getMessage().contains("exceeded expected size"), failure::getMessage);
    }

    @Test
    void truncationDuringReadCannotProduceAPartialFingerprint() throws Exception {
        Path source = tempDir.resolve("truncated-during-read.bin");
        byte[] initial = new byte[32 * 1024];
        Arrays.fill(initial, (byte) 't');
        Files.write(source, initial);

        IOException failure = assertThrows(IOException.class, () -> SourceFingerprint.ofFile(
                source,
                initial.length,
                (path, checkpoint) -> {
                    if (checkpoint == SourceFingerprint.ReadCheckpoint.AFTER_FIRST_READ) {
                        try (var writer = Files.newByteChannel(path, StandardOpenOption.WRITE)) {
                            writer.truncate(8 * 1024L);
                        }
                    }
                }));

        assertTrue(failure.getMessage().contains("changed size"), failure::getMessage);
    }

    @Test
    void inPlaceModificationDuringReadIsRejectedByFinalMetadataCheck() throws Exception {
        Path source = tempDir.resolve("modified-during-read.bin");
        byte[] initial = new byte[32 * 1024];
        Arrays.fill(initial, (byte) 'm');
        Files.write(source, initial);
        FileTime originalMtime = Files.getLastModifiedTime(source);

        IOException failure = assertThrows(IOException.class, () -> SourceFingerprint.ofFile(
                source,
                initial.length,
                (path, checkpoint) -> {
                    if (checkpoint == SourceFingerprint.ReadCheckpoint.AFTER_FIRST_READ) {
                        try (var writer = Files.newByteChannel(path, StandardOpenOption.WRITE)) {
                            writer.position(16 * 1024L);
                            ByteBuffer replacement = ByteBuffer.wrap(new byte[16 * 1024]);
                            while (replacement.hasRemaining()) {
                                writer.write(replacement);
                            }
                        }
                        Files.setLastModifiedTime(path, FileTime.fromMillis(originalMtime.toMillis() + 10_000));
                    }
                }));

        assertTrue(failure.getMessage().contains("identity or metadata"), failure::getMessage);
    }

    @Test
    void replacementBySymbolicLinkBeforeFinalObservationIsRejectedWhenSupported() throws Exception {
        Path source = tempDir.resolve("source-before-link.md");
        Path target = tempDir.resolve("link-target.md");
        Files.writeString(source, "source", StandardCharsets.UTF_8);
        Files.writeString(target, "target", StandardCharsets.UTF_8);
        if (!symbolicLinksSupported(target)) {
            return;
        }

        IOException failure = assertThrows(IOException.class, () -> SourceFingerprint.ofFile(
                source,
                Files.size(source),
                (path, checkpoint) -> {
                    if (checkpoint == SourceFingerprint.ReadCheckpoint.BEFORE_FINAL_ATTRIBUTES) {
                        Files.delete(path);
                        Files.createSymbolicLink(path, target);
                    }
                }));

        assertTrue(failure.getMessage().contains("identity or metadata"), failure::getMessage);
    }

    @Test
    void boundedFingerprintDoesNotFollowFinalSymbolicLinkWhenSupported() throws Exception {
        Path target = tempDir.resolve("outside.md");
        Files.writeString(target, "outside", StandardCharsets.UTF_8);
        Path link = tempDir.resolve("source-link.md");
        if (!tryCreateSymbolicLink(link, target)) {
            return;
        }

        IOException failure = assertThrows(IOException.class, () -> SourceFingerprint.ofFile(link, Files.size(target)));
        assertTrue(failure.getMessage().contains("regular non-symbolic"), failure::getMessage);
    }

    private boolean symbolicLinksSupported(Path target) throws IOException {
        Path probe = tempDir.resolve("symlink-probe");
        if (!tryCreateSymbolicLink(probe, target)) {
            return false;
        }
        Files.delete(probe);
        return true;
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
