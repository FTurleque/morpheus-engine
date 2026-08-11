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
        assertThrows(
                IllegalArgumentException.class,
                () -> roots.requireAllowedDirectory(child.resolve("..").resolve("sub")));
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
    void rejectsSymlinkAliasOutsideRootEvenWhenItTargetsAllowedDirectory() throws Exception {
        Path allowed = Files.createDirectory(temp.resolve("allowed"));
        Path project = Files.createDirectory(allowed.resolve("project"));
        Path alias = temp.resolve("outside-alias");
        if (!createSymlink(alias, project)) return;

        AllowedWorkspaceRoots roots = AllowedWorkspaceRoots.of(List.of(allowed));
        assertThrows(IllegalArgumentException.class, () -> roots.requireAllowedDirectory(alias));
    }

    @Test
    void rejectsWindowsJunctionInsideRoot() throws Exception {
        if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) return;
        Path allowed = Files.createDirectory(temp.resolve("allowed"));
        Path target = Files.createDirectory(temp.resolve("junction-target"));
        Path junction = allowed.resolve("junction");
        Process process = new ProcessBuilder(
                "cmd.exe", "/d", "/c", "mklink", "/J", junction.toString(), target.toString())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, exitCode, output);

        AllowedWorkspaceRoots roots = AllowedWorkspaceRoots.of(List.of(allowed));
        assertThrows(IllegalArgumentException.class, () -> roots.requireAllowedDirectory(junction));
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
