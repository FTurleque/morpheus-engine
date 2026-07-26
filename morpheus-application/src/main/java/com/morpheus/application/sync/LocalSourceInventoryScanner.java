package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Local-first scanner. Watcher events may trigger it, but this content scan remains the source of truth. */
public final class LocalSourceInventoryScanner {
    private final SourceScanPolicy policy;

    public LocalSourceInventoryScanner() {
        this(SourceScanPolicy.safeDefaults());
    }

    public LocalSourceInventoryScanner(SourceScanPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public SourceScanPolicy policy() {
        return policy;
    }

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
        Set<FileVisitOption> visitOptions = policy.followSymbolicLinks()
                ? EnumSet.of(FileVisitOption.FOLLOW_LINKS)
                : EnumSet.noneOf(FileVisitOption.class);

        for (Path root : roots) {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                failures.add(new SourceInventoryScanResult.Failure(
                        Optional.of(display(workspace, root)),
                        "source root does not exist"));
                continue;
            }
            if (!policy.followSymbolicLinks() && Files.isSymbolicLink(root)) {
                failures.add(new SourceInventoryScanResult.Failure(
                        Optional.of(display(workspace, root)),
                        "symbolic-link source root is not followed by the active scan policy"));
                continue;
            }
            try {
                Files.walkFileTree(root, visitOptions, Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                        if (policy.ignoresDirectory(directory)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        if (!policy.followSymbolicLinks() && Files.isSymbolicLink(directory)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (!policy.followSymbolicLinks() && (attrs.isSymbolicLink() || Files.isSymbolicLink(file))) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (!attrs.isRegularFile()) {
                            return FileVisitResult.CONTINUE;
                        }
                        try {
                            SourcePath sourcePath = new SourcePath(workspace.relativize(file.toAbsolutePath().normalize()).toString());
                            SourceFingerprint fingerprint = SourceFingerprint.ofFile(file);
                            BasicFileAttributes after = Files.readAttributes(
                                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                            if (!after.isRegularFile()
                                    || attrs.size() != after.size()
                                    || !attrs.lastModifiedTime().equals(after.lastModifiedTime())) {
                                failures.add(new SourceInventoryScanResult.Failure(
                                        Optional.of(sourcePath.toString()),
                                        "source changed while fingerprint was being computed"));
                                return FileVisitResult.CONTINUE;
                            }
                            SourceInventory.Entry entry = new SourceInventory.Entry(
                                    sourcePath,
                                    fingerprint,
                                    after.size());
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
