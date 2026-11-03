package com.morpheus.application.sync;

import java.io.IOException;

/**
 * A path the scan or the watcher refuses at the physical workspace boundary: it leaves the workspace, crosses a
 * symbolic link, or its workspace root is one. The scan reports it as {@code WORKSPACE_BOUNDARY_ESCAPED}, never as an
 * unreadable source (ADR-0157).
 */
final class WorkspaceBoundaryEscapeException extends IOException {
    WorkspaceBoundaryEscapeException(String message) {
        super(message);
    }
}
