package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Local-first scanner. Watcher events may trigger it, but this content scan remains the source of truth. */
public final class LocalSourceInventoryScanner {

    public SourceInventoryScanResult scan(
            Path workspaceRoot,
            ProjectSpecificationId projectId,
            Optional<String> sourceRevision,
            Instant capturedAt,
            Collection<Path> sourceRoots) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(sourceRoots, "sourceRoots");

        Path workspace = workspaceRoot.toAbsolutePath().normalize();
        List<Path> roots = sourceRoots.isEmpty()
                ? List.of(workspace)
                : sourceRoots.stream()
                        .map(root -> resolveWithin(workspace, root))
                        .distinct()
                        .sorted()
                        .toList();

        Map<SourcePath, SourceInventory.Entry> entries = new LinkedHashMap<>();
        List<SourceInventoryScanResult.Failure> failures = new ArrayList<>();

        for (Path root : roots) {
            if (!Files.exists(root)) {
                failures.add(new SourceInventoryScanResult.Failure(
                        Optional.of(display(workspace, root)),
                        "source root does not exist"));
                continue;
            }
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (!attrs.isRegularFile()) {
                            return FileVisitResult.CONTINUE;
                        }
                        try {
                            SourcePath sourcePath = new SourcePath(workspace.relativize(file.toAbsolutePath().normalize()).toString());
                            SourceInventory.Entry entry = new SourceInventory.Entry(
                                    sourcePath,
                                    SourceFingerprint.ofFile(file),
                                    attrs.size());
                            SourceInventory.Entry existing = entries.putIfAbsent(sourcePath, entry);
                            if (existing != null && !existing.equals(entry)) {
                                failures.add(new SourceInventoryScanResult.Failure(
                                        Optional.of(sourcePath.toString()),
                                        "source observed twice with different content"));
                            }
                        } catch (IOException | RuntimeException exception) {
                            failures.add(new SourceInventoryScanResult.Failure(
                                    Optional.of(display(workspace, file)),
                                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exception) {
                        failures.add(new SourceInventoryScanResult.Failure(
                                Optional.of(display(workspace, file)),
                                exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException | RuntimeException exception) {
                failures.add(new SourceInventoryScanResult.Failure(
                        Optional.of(display(workspace, root)),
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
            }
        }

        if (!failures.isEmpty()) {
            return SourceInventoryScanResult.incomplete(projectId, failures);
        }
        return SourceInventoryScanResult.complete(new SourceInventory(
                projectId,
                sourceRevision,
                capturedAt,
                List.copyOf(entries.values())));
    }

    private Path resolveWithin(Path workspace, Path root) {
        Objects.requireNonNull(root, "sourceRoots item");
        Path resolved = (root.isAbsolute() ? root : workspace.resolve(root)).toAbsolutePath().normalize();
        if (!resolved.startsWith(workspace)) {
            throw new IllegalArgumentException("source root escapes workspace: " + root);
        }
        return resolved;
    }

    private static String display(Path workspace, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(workspace)
                ? workspace.relativize(normalized).toString().replace('\\', '/')
                : normalized.toString();
    }
}
