package com.morpheus.application.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Physical workspace-boundary validation shared by scanner and watcher. */
final class WorkspacePathBoundary {
    private WorkspacePathBoundary() {
    }

    static void requireContained(Path workspaceRoot, Path candidate) throws IOException {
        Path workspace = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        Path path = Objects.requireNonNull(candidate, "candidate").toAbsolutePath().normalize();
        if (!path.startsWith(workspace)) {
            throw new IOException("source path escapes workspace");
        }
        if (!Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(workspace)) {
            throw new IOException("workspace root must not be a symbolic link");
        }

        Path physicalWorkspace = workspace.toRealPath();
        Path current = workspace;
        Path relative = workspace.relativize(path);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            if (Files.isSymbolicLink(current)) {
                throw new IOException("source path crosses a symbolic link inside the workspace");
            }
            Path physicalCurrent = current.toRealPath();
            if (Files.isSymbolicLink(current) || !physicalCurrent.startsWith(physicalWorkspace)) {
                throw new IOException("source path resolves outside the workspace");
            }
        }
    }
}
