package com.morpheus.application.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeWorkspaceFileResolverReplacementTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteAndRecreateWithSameSizeAndMtimeCannotReturnReplacementContent() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path source = workspace.resolve("spec.md");
        Path replacement = tempDir.resolve("replacement.md");
        Files.writeString(source, "AAAA", StandardCharsets.UTF_8);
        Files.writeString(replacement, "BBBB", StandardCharsets.UTF_8);
        FileTime commonMtime = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(source, commonMtime);
        Files.setLastModifiedTime(replacement, commonMtime);
        assertEquals(Files.size(source), Files.size(replacement));
        assertEquals(Files.getLastModifiedTime(source), Files.getLastModifiedTime(replacement));

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(
                workspace,
                file -> {
                    Files.delete(file);
                    Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING);
                });

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.readUtf8(Path.of("spec.md")));

        assertTrue(failure.getMessage().contains("identity or metadata"), failure::getMessage);
        assertTrue(failure.getMessage().contains("content"), failure::getMessage);
    }
}
