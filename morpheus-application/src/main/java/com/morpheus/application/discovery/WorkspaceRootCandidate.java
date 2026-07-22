package com.morpheus.application.discovery;

import java.nio.file.Path;
import java.util.Objects;

/** One deterministic workspace root candidate considered by discovery. */
public record WorkspaceRootCandidate(Path root, WorkspaceRootKind kind) {

    public WorkspaceRootCandidate {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Objects.requireNonNull(kind, "kind");
    }
}
