package com.morpheus.application.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The single-path check the watcher and the source-root check use: a path inside the workspace passes, whether it
 * exists yet or not, and a path that leaves the workspace lexically, crosses a link inside it, or sits under a root that
 * is itself a link is refused by name.
 */
class WorkspacePathBoundaryTest {
    @TempDir
    Path temp;

    @Test
    void aFileAndADirectoryInsideTheWorkspaceAreContained() throws IOException {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        Path docs = Files.createDirectories(workspace.resolve("area/docs"));
        Path file = Files.writeString(docs.resolve("doc.md"), "doc");

        assertDoesNotThrow(() -> WorkspacePathBoundary.requireContained(workspace, docs));
        assertDoesNotThrow(() -> WorkspacePathBoundary.requireContained(workspace, file));
        assertDoesNotThrow(() -> WorkspacePathBoundary.requireContained(workspace, workspace));
    }

    @Test
    void aPathThatLeavesTheWorkspaceLexicallyIsRefusedBeforeAnyFileSystemQuery() throws IOException {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        Path sibling = Files.writeString(temp.resolve("outside.md"), "outside");

        WorkspaceBoundaryEscapeException direct = assertThrows(WorkspaceBoundaryEscapeException.class,
                () -> WorkspacePathBoundary.requireContained(workspace, sibling));
        WorkspaceBoundaryEscapeException dotted = assertThrows(WorkspaceBoundaryEscapeException.class,
                () -> WorkspacePathBoundary.requireContained(workspace, workspace.resolve("area/../../outside.md")));

        assertEquals("source path escapes workspace", direct.getMessage());
        assertEquals("source path escapes workspace", dotted.getMessage());
    }

    @Test
    void aPathThatCrossesALinkInsideTheWorkspaceIsRefused() throws IOException {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        Path elsewhere = Files.createDirectories(temp.resolve("elsewhere/docs"));
        Files.writeString(elsewhere.resolve("doc.md"), "doc");
        assumeTrue(createSymlink(workspace.resolve("area"), elsewhere.getParent()),
                "symbolic links cannot be created in this environment");

        WorkspaceBoundaryEscapeException refusal = assertThrows(WorkspaceBoundaryEscapeException.class,
                () -> WorkspacePathBoundary.requireContained(workspace, workspace.resolve("area/docs/doc.md")));

        assertEquals("source path crosses a symbolic link inside the workspace", refusal.getMessage());
    }

    @Test
    void aWorkspaceRootThatIsALinkIsRefused() throws IOException {
        Path real = Files.createDirectories(temp.resolve("real/docs"));
        Path workspace = temp.resolve("linked-workspace");
        assumeTrue(createSymlink(workspace, real.getParent()), "symbolic links cannot be created in this environment");

        WorkspaceBoundaryEscapeException refusal = assertThrows(WorkspaceBoundaryEscapeException.class,
                () -> WorkspacePathBoundary.requireContained(workspace, workspace.resolve("docs")));

        assertEquals("workspace root must not be a symbolic link", refusal.getMessage());
    }

    private static boolean createSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return false;
        }
    }
}
