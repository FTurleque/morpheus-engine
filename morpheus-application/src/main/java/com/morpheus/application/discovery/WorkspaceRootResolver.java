package com.morpheus.application.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Produces deterministic workspace root candidates without invoking the Git executable.
 *
 * <p>The explicit path is always first. A distinct Git ancestor is only a fallback candidate.
 */
public final class WorkspaceRootResolver {

    public List<WorkspaceRootCandidate> candidates(Path requestedPath) {
        Objects.requireNonNull(requestedPath, "requestedPath");

        Path explicitRoot = requestedPath.toAbsolutePath().normalize();
        List<WorkspaceRootCandidate> result = new ArrayList<>();
        result.add(new WorkspaceRootCandidate(explicitRoot, WorkspaceRootKind.EXPLICIT));

        findGitRoot(explicitRoot)
                .filter(gitRoot -> !gitRoot.equals(explicitRoot))
                .ifPresent(gitRoot -> result.add(
                        new WorkspaceRootCandidate(gitRoot, WorkspaceRootKind.GIT_ANCESTOR)));

        return List.copyOf(result);
    }

    Optional<Path> findGitRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            Path marker = current.resolve(".git");
            if (Files.isDirectory(marker) || Files.isRegularFile(marker)) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }
}
