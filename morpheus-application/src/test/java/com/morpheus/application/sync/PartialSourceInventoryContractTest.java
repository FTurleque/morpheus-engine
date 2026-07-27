package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartialSourceInventoryContractTest {

    @TempDir
    Path tempDir;

    @Test
    void missingSourceRootProducesExplicitIncompleteScanAndNoPublishableInventory() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace.resolve("present"));
        Files.writeString(workspace.resolve("present/spec.md"), "present source");

        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner();
        SourceInventoryScanResult result = scanner.scan(
                workspace,
                ProjectSpecificationId.generate(),
                Optional.of("m19-partial"),
                Instant.parse("2026-07-26T20:00:00Z"),
                List.of(Path.of("present"), Path.of("missing")));

        assertFalse(result.complete(), "a partial source set must not be reported as complete");
        assertTrue(result.inventory().isEmpty(), "an incomplete scan must not expose a publishable inventory");
        assertFalse(result.failures().isEmpty());
        assertTrue(result.failures().stream().anyMatch(failure ->
                        failure.source().orElse("").replace('\\', '/').endsWith("missing")
                                && failure.message().contains("does not exist")),
                () -> "missing source root must remain explicit: " + result.failures());
    }
}
