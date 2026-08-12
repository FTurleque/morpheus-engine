package com.morpheus.application.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void boundedReadAcceptsExactBytesAndRejectsTheNextByte() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Files.writeString(workspace.resolve("spec.md"), "€");

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(workspace);
        assertEquals("€", resolver.readUtf8(Path.of("spec.md"), 3));
        assertThrows(IllegalArgumentException.class, () -> resolver.readUtf8(Path.of("spec.md"), 2));
    }

    @Test
    void rejectsMalformedUtf8InsteadOfReplacingInvalidBytes() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace-invalid-utf8"));
        Files.write(workspace.resolve("spec.md"), new byte[]{(byte) 0xC3, (byte) 0x28});

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(workspace);
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.readUtf8(Path.of("spec.md")));
        assertTrue(failure.getMessage().contains("not valid UTF-8"));
    }

    @Test
    void rejectsTraversalOutsideWorkspace() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Files.writeString(temp.resolve("secret.txt"), "secret");

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(workspace);
        assertThrows(IllegalArgumentException.class, () -> resolver.readUtf8(Path.of("../secret.txt")));
        assertThrows(IllegalArgumentException.class, () -> resolver.readUtf8(Path.of("nested/../spec.md")));
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

    @Test
    void rejectsSymlinkAncestorEvenWhenTargetRemainsInsideWorkspace() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path target = Files.createDirectory(workspace.resolve("target"));
        Files.writeString(target.resolve("spec.md"), "safe-but-aliased");
        Path link = workspace.resolve("linked");
        if (!createSymlink(link, target)) return;

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(workspace);
        assertThrows(IllegalArgumentException.class, () -> resolver.readUtf8(Path.of("linked/spec.md")));
    }

    @Test
    void rejectsWindowsJunctionAncestor() throws Exception {
        if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) return;
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path target = Files.createDirectory(temp.resolve("junction-target"));
        Files.writeString(target.resolve("spec.md"), "secret");
        Path junction = workspace.resolve("junction");
        Process process = new ProcessBuilder(
                "cmd.exe", "/d", "/c", "mklink", "/J", junction.toString(), target.toString())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, exitCode, output);

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(workspace);
        assertThrows(IllegalArgumentException.class, () -> resolver.readUtf8(Path.of("junction/spec.md")));
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
