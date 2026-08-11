package com.morpheus.application.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeWorkspaceFileResolverTest {
    @TempDir
    Path temp;

    @Test
    void readsRegularFileInsideWorkspace() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Files.writeString(workspace.resolve("spec.md"), "safe");

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(workspace);
        assertEquals("safe", resolver.readUtf8(Path.of("spec.md")));
    }

    @Test
    void rejectsTraversalOutsideWorkspace() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Files.writeString(temp.resolve("secret.txt"), "secret");

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(workspace);
        assertThrows(IllegalArgumentException.class, () -> resolver.readUtf8(Path.of("../secret.txt")));
    }

    @Test
    void rejectsFinalSymlinkOutsideWorkspaceWhenSupported() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path secret = temp.resolve("secret.txt");
        Files.writeString(secret, "secret");
        Path link = workspace.resolve("spec.md");
        if (!createSymlink(link, secret)) return;

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(workspace);
        assertThrows(IllegalArgumentException.class, () -> resolver.readUtf8(Path.of("spec.md")));
    }

    @Test
    void rejectsSymlinkAncestorOutsideWorkspaceWhenSupported() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Files.writeString(outside.resolve("spec.md"), "secret");
        Path link = workspace.resolve("linked");
        if (!createSymlink(link, outside)) return;

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(workspace);
        assertThrows(IllegalArgumentException.class, () -> resolver.readUtf8(Path.of("linked/spec.md")));
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
