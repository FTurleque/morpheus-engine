package com.morpheus.application.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The physical workspace boundary of one source scan (ADR-0157).
 *
 * <p>Before an entry is read, every segment of its path, the workspace root included, is asked of the file system
 * whether it is a symbolic link, and no remembered answer stands in for that question. What a validated and entered
 * directory spares is the resolution of the segments above the entry, never the link test: only the entry's own
 * segment is resolved again. After a file is fingerprinted the question is "still?": it is asked again, for every
 * segment, with every test. The directories remembered belong to the source root being walked and are dropped with it;
 * nothing outlives the scan.</p>
 */
final class ScanWorkspaceBoundary {
    private static final String ROOT_IS_A_LINK = "workspace root must not be a symbolic link";
    private static final String CROSSES_A_LINK = "source path crosses a symbolic link inside the workspace";

    private final Path workspace;
    private final Set<Path> validatedDirectories = new HashSet<>();
    /**
     * Resolved again by every full walk and kept between them: before each read the root is tested for a link, and a
     * change above the root moves every entry resolved afterwards outside this value, which refuses the entry.
     */
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
            requireNoSegmentBecameALink(parent, Phase.BEFORE_READING);
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
            throw new WorkspaceBoundaryEscapeException("source path escapes workspace");
        }
        return path;
    }

    /** The link test on the workspace root and on every segment down to {@code directory}, one query each. */
    private void requireNoSegmentBecameALink(Path directory, Phase phase) throws IOException {
        if (isSymbolicLink(workspace, phase)) {
            throw new WorkspaceBoundaryEscapeException(ROOT_IS_A_LINK);
        }
        Path current = workspace;
        for (Path segment : segmentsBelowTheWorkspace(directory)) {
            current = current.resolve(segment);
            if (isSymbolicLink(current, phase)) {
                throw new WorkspaceBoundaryEscapeException(CROSSES_A_LINK);
            }
        }
    }

    private Iterable<Path> segmentsBelowTheWorkspace(Path path) {
        return path.equals(workspace) ? List.of() : workspace.relativize(path);
    }

    private void requireEverySegmentContained(Path path, Phase phase) throws IOException {
        if (!exists(workspace, phase)) {
            return;
        }
        if (isSymbolicLink(workspace, phase)) {
            throw new WorkspaceBoundaryEscapeException(ROOT_IS_A_LINK);
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
            throw new WorkspaceBoundaryEscapeException(CROSSES_A_LINK);
        }
        Path physicalCurrent = realPath(current, phase);
        // A second look, because the segment may have been swapped for a link that resolves inside the workspace
        // between the test above and the resolution: it is the only thing that refuses that replacement.
        if (isSymbolicLink(current, phase) || !physicalCurrent.startsWith(physicalWorkspace)) {
            throw new WorkspaceBoundaryEscapeException("source path resolves outside the workspace");
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
