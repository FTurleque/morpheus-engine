package com.morpheus.provider.openspec;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSpecBoundedTraversalTest {
    @TempDir
    Path temp;

    @Test
    void enumeratesRegularTreeWithinConfiguredBounds() throws Exception {
        Path root = Files.createDirectories(temp.resolve("openspec/specs/a"));
        Path document = Files.writeString(root.resolve("spec.md"), "# A");

        List<Path> paths;
        try (var walk = OpenSpecBoundedTraversal.walk(temp.resolve("openspec/specs"), 4, 16)) {
            paths = walk.toList();
        }

        assertTrue(paths.contains(document.toAbsolutePath().normalize()));
    }

    @Test
    void rejectsTraversalBeyondMaximumDepth() throws Exception {
        Path root = Files.createDirectories(temp.resolve("deep"));
        Path current = root;
        for (int index = 0; index < 5; index++) {
            current = Files.createDirectory(current.resolve("level-" + index));
        }
        Files.writeString(current.resolve("spec.md"), "# Deep");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
            try (var ignored = OpenSpecBoundedTraversal.walk(root, 3, 64)) {
                ignored.toList();
            }
        });
        assertTrue(failure.getMessage().contains("maximum depth"));
    }

    @Test
    void rejectsTraversalBeyondVisitedEntryBudget() throws Exception {
        Path root = Files.createDirectories(temp.resolve("wide"));
        for (int index = 0; index < 8; index++) {
            Files.writeString(root.resolve("entry-" + index + ".md"), "# " + index);
        }

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
            try (var ignored = OpenSpecBoundedTraversal.walk(root, 4, 5)) {
                ignored.toList();
            }
        });
        assertTrue(failure.getMessage().contains("visited-entry budget"));
    }

    @Test
    void rejectsSymbolicLinkEntriesInsteadOfSilentlySkippingThem() throws Exception {
        Path root = Files.createDirectories(temp.resolve("links"));
        Path target = Files.writeString(temp.resolve("outside.md"), "outside");
        Path link = root.resolve("spec.md");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException failure) {
            Assumptions.abort("symbolic links are unavailable on this platform");
        }

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> {
            try (var ignored = OpenSpecBoundedTraversal.walk(root, 4, 16)) {
                ignored.toList();
            }
        });
        assertTrue(failure.getMessage().contains("symbolic link"));
    }
}
