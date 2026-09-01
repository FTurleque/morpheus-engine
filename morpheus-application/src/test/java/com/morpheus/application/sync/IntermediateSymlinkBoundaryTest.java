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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntermediateSymlinkBoundaryTest {
    @TempDir
    Path temp;

    @Test
    void scannerRejectsRootWhoseIntermediateComponentEscapesWorkspace() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path outside = Files.createDirectories(temp.resolve("outside/nested"));
        Files.writeString(outside.resolve("outside.md"), "outside");
        Path link = workspace.resolve("link");
        if (!createSymlink(link, outside.getParent())) return;

        SourceInventoryScanResult result = new LocalSourceInventoryScanner().scan(
                workspace,
                ProjectSpecificationId.generate(),
                Optional.empty(),
                Instant.parse("2026-08-30T12:00:00Z"),
                List.of(Path.of("link/nested")));

        assertFalse(result.complete());
        assertTrue(result.inventory().isEmpty());
        assertTrue(result.failures().stream().anyMatch(failure ->
                failure.message().contains("symbolic link") || failure.message().contains("outside the workspace")));
    }

    @Test
    void watcherRejectsRootWhoseIntermediateComponentEscapesWorkspace() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("watch-workspace"));
        Path outside = Files.createDirectories(temp.resolve("watch-outside/nested"));
        Path link = workspace.resolve("link");
        if (!createSymlink(link, outside.getParent())) return;

        assertThrows(IOException.class,
                () -> new LocalSourceWatcher(workspace, List.of(Path.of("link/nested"))));
    }

    private boolean createSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            return false;
        }
    }
}
