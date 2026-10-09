package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
        assumeTrue(createSymlink(link, outside), "symbolic links cannot be created in this environment");

        AllowedWorkspaceRoots roots = AllowedWorkspaceRoots.of(List.of(allowed));
        assertThrows(IllegalArgumentException.class, () -> roots.requireAllowedDirectory(link));
    }

    @Test
    void rejectsSymlinkAliasOutsideRootEvenWhenItTargetsAllowedDirectory() throws Exception {
        Path allowed = Files.createDirectory(temp.resolve("allowed"));
        Path project = Files.createDirectory(allowed.resolve("project"));
        Path alias = temp.resolve("outside-alias");
        assumeTrue(createSymlink(alias, project), "symbolic links cannot be created in this environment");

        AllowedWorkspaceRoots roots = AllowedWorkspaceRoots.of(List.of(allowed));
        assertThrows(IllegalArgumentException.class, () -> roots.requireAllowedDirectory(alias));
    }

    @Test
    void rejectsRootReplacementAfterAllowlistCreation() throws Exception {
        Path allowed = Files.createDirectory(temp.resolve("allowed"));
        AllowedWorkspaceRoots roots = AllowedWorkspaceRoots.of(List.of(allowed));
        awaitFileSystemClockTickAfterCreationOf(allowed);
        Path original = temp.resolve("allowed-original");
        Files.move(allowed, original);
        Path replacement = Files.createDirectory(allowed);

        IllegalArgumentException rejection =
                assertThrows(IllegalArgumentException.class, () -> roots.requireAllowedDirectory(replacement));

        // This check runs while serving a request and the remote facade renders the failure as a 400 carrying
        // this message, so naming the root would publish a server-configured pathname the caller cannot select.
        assertFalse(rejection.getMessage().contains(allowed.toString()),
                () -> "root replacement rejection must not name the server-configured root: " + rejection.getMessage());
        assertFalse(rejection.getMessage().contains(temp.toString()),
                () -> "root replacement rejection must not name a server location: " + rejection.getMessage());
    }

    @Test
    void rejectsWindowsJunctionInsideRoot() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "NTFS junctions exist only on Windows");
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
    void rejectsEmptyRootConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> AllowedWorkspaceRoots.of(List.of()));
    }

    @Test
    void rejectsSymbolicRootConfiguration() throws Exception {
        Path target = Files.createDirectory(temp.resolve("target"));
        Path link = temp.resolve("root-link");
        assumeTrue(createSymlink(link, target), "symbolic links cannot be created in this environment");

        assertThrows(IllegalArgumentException.class, () -> AllowedWorkspaceRoots.of(List.of(link)));
    }

    /**
     * Without a file key (Windows) a root is identified by its owner and creation time, and the file system stamps
     * creation times with a clock that ticks about every 15 ms. A real allowed root was created long before it is
     * replaced; a test that creates it, moves it and recreates it within one tick gets two directories with the
     * same creation time and no way to tell them apart. Measured on Windows: 15 of 200 such replacements
     * collided, none of 200 once the original was older than one tick, move and recreation still immediate.
     * Waiting for a directory created now to carry a later creation time reproduces the production precondition
     * without a fixed sleep.
     */
    private void awaitFileSystemClockTickAfterCreationOf(Path root) throws IOException {
        java.nio.file.attribute.FileTime rootCreated = Files.readAttributes(
                root, java.nio.file.attribute.BasicFileAttributes.class).creationTime();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        for (int attempt = 0; System.nanoTime() < deadline; attempt++) {
            Path probe = Files.createDirectory(temp.resolve("clock-probe-" + attempt));
            if (Files.readAttributes(probe, java.nio.file.attribute.BasicFileAttributes.class)
                    .creationTime().compareTo(rootCreated) > 0) {
                return;
            }
        }
        throw new AssertionError("the file system clock did not advance within 5 s of the root's creation");
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
