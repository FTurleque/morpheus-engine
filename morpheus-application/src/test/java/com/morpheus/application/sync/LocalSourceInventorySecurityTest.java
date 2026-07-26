package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSourceInventorySecurityTest {

    @TempDir
    Path tempDir;

    @Test
    void safeDefaultsIgnoreGeneratedAndRepositoryMetadataDirectories() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace.resolve("openspec"));
        Files.createDirectories(workspace.resolve(".git"));
        Files.createDirectories(workspace.resolve("target"));
        Files.createDirectories(workspace.resolve("node_modules/pkg"));
        Files.writeString(workspace.resolve("openspec/spec.md"), "accepted");
        Files.writeString(workspace.resolve(".git/config"), "secret-ish repository metadata");
        Files.writeString(workspace.resolve("target/generated.md"), "generated");
        Files.writeString(workspace.resolve("node_modules/pkg/index.md"), "dependency");

        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner();
        var result = scanner.scan(
                workspace,
                ProjectSpecificationId.generate(),
                Optional.empty(),
                Instant.parse("2026-07-26T18:00:00Z"),
                List.of());

        assertTrue(result.complete(), () -> "scan failures: " + result.failures());
        var entries = result.inventory().orElseThrow().entries();
        assertEquals(1, entries.size());
        assertEquals("openspec/spec.md", entries.getFirst().path().toString());
        assertFalse(scanner.policy().followSymbolicLinks());
        assertTrue(scanner.policy().ignoredDirectoryNames().contains(".git"));
        assertTrue(scanner.policy().ignoredDirectoryNames().contains("target"));
    }

    @Test
    void safeDefaultDoesNotTraverseExternalSymbolicLinkWhenPlatformSupportsLinks() throws Exception {
        Path workspace = tempDir.resolve("workspace-links");
        Path external = tempDir.resolve("external");
        Files.createDirectories(workspace);
        Files.createDirectories(external);
        Files.writeString(workspace.resolve("local.md"), "local");
        Files.writeString(external.resolve("outside.md"), "outside");

        boolean linkCreated = tryCreateSymbolicLink(workspace.resolve("external-link"), external);
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner();
        var result = scanner.scan(
                workspace,
                ProjectSpecificationId.generate(),
                Optional.empty(),
                Instant.parse("2026-07-26T18:00:00Z"),
                List.of());

        assertTrue(result.complete(), () -> "scan failures: " + result.failures());
        assertEquals(List.of("local.md"), result.inventory().orElseThrow().entries().stream()
                .map(entry -> entry.path().toString())
                .toList());
        assertFalse(scanner.policy().followSymbolicLinks());

        if (linkCreated) {
            assertTrue(Files.isSymbolicLink(workspace.resolve("external-link")));
        }
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
