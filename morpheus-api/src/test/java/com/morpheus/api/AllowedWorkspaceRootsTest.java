package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AllowedWorkspaceRootsTest {
    @TempDir
    Path temp;

    @Test
    void acceptsRootAndDescendantButRejectsOutsideDirectory() throws Exception {
        Path allowed = Files.createDirectory(temp.resolve("allowed"));
        Path child = Files.createDirectories(allowed.resolve("project/sub"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        AllowedWorkspaceRoots roots = AllowedWorkspaceRoots.of(List.of(allowed));

        assertEquals(allowed.toRealPath(), roots.requireAllowedDirectory(allowed));
        assertEquals(child.toRealPath(), roots.requireAllowedDirectory(child));
        assertThrows(IllegalArgumentException.class, () -> roots.requireAllowedDirectory(outside));
    }

    @Test
    void rejectsSymlinkedWorkspaceWhenSupported() throws Exception {
        Path allowed = Files.createDirectory(temp.resolve("allowed"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Path link = allowed.resolve("linked");
        if (!createSymlink(link, outside)) return;

        AllowedWorkspaceRoots roots = AllowedWorkspaceRoots.of(List.of(allowed));
        assertThrows(IllegalArgumentException.class, () -> roots.requireAllowedDirectory(link));
    }

    @Test
    void rejectsEmptyOrSymbolicRootConfiguration() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> AllowedWorkspaceRoots.of(List.of()));
        Path target = Files.createDirectory(temp.resolve("target"));
        Path link = temp.resolve("root-link");
        if (createSymlink(link, target)) {
            assertThrows(IllegalArgumentException.class, () -> AllowedWorkspaceRoots.of(List.of(link)));
        }
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
