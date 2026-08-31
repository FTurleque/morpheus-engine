package com.morpheus.application.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void defaultReadRejectsFilesLargerThanOneMiB() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace-default-limit"));
        Files.write(workspace.resolve("too-large.txt"), new byte[(1024 * 1024) + 1]);

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(workspace);
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.readUtf8(Path.of("too-large.txt")));
        assertTrue(failure.getMessage().contains("1048576 bytes"), failure::getMessage);
    }

    @Test
    void rejectsSameSizeSameMtimeReplacementWithoutReturningStaleContent() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace-replacement"));
        Path source = workspace.resolve("spec.md");
        Path replacement = temp.resolve("replacement.md");
        Files.writeString(source, "AAAA");
        Files.writeString(replacement, "BBBB");
        alignReplacementMetadata(source, replacement);

        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(
                workspace,
                file -> replaceAtomically(replacement, file));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.readUtf8(Path.of("spec.md")));
        assertTrue(failure.getMessage().contains("changed identity or metadata"), failure::getMessage);
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

    private void alignReplacementMetadata(Path first, Path second) throws IOException {
        FileTime commonMtime = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(first, commonMtime);
        Files.setLastModifiedTime(second, commonMtime);

        BasicFileAttributes firstAttributes = readAttributes(first);
        BasicFileAttributes secondAttributes = readAttributes(second);
        if (firstAttributes.fileKey() == null && secondAttributes.fileKey() == null) {
            setCreationTime(first, FileTime.fromMillis(1_600_000_000_000L), commonMtime);
            setCreationTime(second, FileTime.fromMillis(1_650_000_000_000L), commonMtime);
            firstAttributes = readAttributes(first);
            secondAttributes = readAttributes(second);
        }

        assertEquals(firstAttributes.size(), secondAttributes.size());
        assertEquals(firstAttributes.lastModifiedTime(), secondAttributes.lastModifiedTime());
        if (firstAttributes.fileKey() == null && secondAttributes.fileKey() == null) {
            assertNotEquals(firstAttributes.creationTime(), secondAttributes.creationTime());
        } else {
            assertNotEquals(firstAttributes.fileKey(), secondAttributes.fileKey());
        }
    }

    private BasicFileAttributes readAttributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private void setCreationTime(Path path, FileTime creationTime, FileTime lastModifiedTime) throws IOException {
        BasicFileAttributeView view = Files.getFileAttributeView(
                path, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        view.setTimes(lastModifiedTime, null, creationTime);
    }

    private void replaceAtomically(Path replacement, Path target) throws IOException {
        try {
            Files.move(
                    replacement,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(replacement, target, StandardCopyOption.REPLACE_EXISTING);
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
