package com.morpheus.application.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The physical workspace boundary of one source scan.
 *
 * <p>Before an entry is read, a directory this scan has already validated and entered answers for every segment above
 * it, so only the entry's own segment is asked. After a file is fingerprinted the question is "still?": it is asked of
 * the file system again, for every segment, and never answered from what the scan remembered. The directories
 * remembered belong to the source root being walked and are dropped with it; nothing outlives the scan.</p>
 */
final class ScanWorkspaceBoundary {
    private final Path workspace;
    private final Set<Path> validatedDirectories = new HashSet<>();
    private Path physicalWorkspace;
    private long queriesBeforeReading;
    private long queriesRevalidating;

    ScanWorkspaceBoundary(Path workspaceRoot) {
        this.workspace = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
    }

    /** Forgets the directories validated for the previous source root. */
    void enterSourceRoot() {
        validatedDirectories.clear();
    }

    /** Records that the scan validated {@code directory} and is about to read what is beneath it. */
    void markEntered(Path directory) {
        validatedDirectories.add(directory.toAbsolutePath().normalize());
    }

    void requireContainedBeforeReading(Path candidate) throws IOException {
        Path path = lexicallyContained(candidate);
        if (validatedDirectories.contains(path)) {
            return;
        }
        Path parent = path.getParent();
        if (physicalWorkspace != null && parent != null && validatedDirectories.contains(parent)) {
            requireSegmentContained(path, Phase.BEFORE_READING);
            return;
        }
        requireEverySegmentContained(path, Phase.BEFORE_READING);
    }

    void requireStillContained(Path file) throws IOException {
        requireEverySegmentContained(lexicallyContained(file), Phase.REVALIDATING);
    }

    long queriesBeforeReading() {
        return queriesBeforeReading;
    }

    long queriesRevalidating() {
        return queriesRevalidating;
    }

    private Path lexicallyContained(Path candidate) throws IOException {
        Path path = Objects.requireNonNull(candidate, "candidate").toAbsolutePath().normalize();
        if (!path.startsWith(workspace)) {
            throw new IOException("source path escapes workspace");
        }
        return path;
    }

    private void requireEverySegmentContained(Path path, Phase phase) throws IOException {
        if (!exists(workspace, phase)) {
            return;
        }
        if (isSymbolicLink(workspace, phase)) {
            throw new IOException("workspace root must not be a symbolic link");
        }
        physicalWorkspace = realPath(workspace, phase);
        Path current = workspace;
        for (Path segment : workspace.relativize(path)) {
            current = current.resolve(segment);
            if (!requireSegmentContained(current, phase)) {
                return;
            }
        }
    }

    /** Whether the segment exists; a segment that is not there cannot escape. */
    private boolean requireSegmentContained(Path current, Phase phase) throws IOException {
        if (!exists(current, phase)) {
            return false;
        }
        if (isSymbolicLink(current, phase)) {
            throw new IOException("source path crosses a symbolic link inside the workspace");
        }
        Path physicalCurrent = realPath(current, phase);
        // A second look, because the segment may have been swapped for a link that resolves inside the workspace
        // between the test above and the resolution: it is the only thing that refuses that replacement.
        if (isSymbolicLink(current, phase) || !physicalCurrent.startsWith(physicalWorkspace)) {
            throw new IOException("source path resolves outside the workspace");
        }
        return true;
    }

    private boolean exists(Path path, Phase phase) {
        count(phase);
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    private boolean isSymbolicLink(Path path, Phase phase) {
        count(phase);
        return Files.isSymbolicLink(path);
    }

    private Path realPath(Path path, Phase phase) throws IOException {
        count(phase);
        return path.toRealPath();
    }

    private void count(Phase phase) {
        if (phase == Phase.BEFORE_READING) {
            queriesBeforeReading++;
        } else {
            queriesRevalidating++;
        }
    }

    private enum Phase {
        BEFORE_READING,
        REVALIDATING
    }
}
