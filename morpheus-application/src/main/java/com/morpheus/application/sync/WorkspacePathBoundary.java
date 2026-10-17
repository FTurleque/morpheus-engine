package com.morpheus.application.sync;

import java.io.IOException;
import java.nio.file.Path;

/** Physical workspace-boundary validation of a single path, for the watcher and for the source-root check. */
final class WorkspacePathBoundary {
    private WorkspacePathBoundary() {
    }

    static void requireContained(Path workspaceRoot, Path candidate) throws IOException {
        new ScanWorkspaceBoundary(workspaceRoot).requireStillContained(candidate);
    }
}
