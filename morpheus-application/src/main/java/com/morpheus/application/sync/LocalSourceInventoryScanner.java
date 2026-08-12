package com.morpheus.application.sync;

import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.operability.OperationalEventCode;
import com.morpheus.application.operability.OperationalRecorder;
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

/** Local-first scanner. Watcher events may trigger it, but this bounded content scan remains the source of truth. */
public final class LocalSourceInventoryScanner {
    private final SourceScanPolicy policy;
    private final OperationalRecorder recorder;

    public LocalSourceInventoryScanner() {
        this(SourceScanPolicy.safeDefaults(), LocalOperationalRuntime.recorder());
    }

    public LocalSourceInventoryScanner(SourceScanPolicy policy) {
        this(policy, LocalOperationalRuntime.recorder());
    }

    public LocalSourceInventoryScanner(SourceScanPolicy policy, OperationalRecorder recorder) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
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
        OperationalRecorder.Operation operation = recorder.begin(
                "source.scan",
                OperationalEventCode.SYNC_STARTED,
                Map.of("projectId", Objects.requireNonNull(projectId, "projectId").toString()));
        try {
            SourceInventoryScanResult result = scanInternal(
                    workspaceRoot, projectId, sourceRevision, capturedAt, sourceRoots);
            if (result.complete()) {
                int sourceCount = result.inventory().orElseThrow().entries().size();
                recorder.metrics().add("source.scan.file_count", sourceCount);
                operation.success(
                        OperationalEventCode.SYNC_COMPLETED,
                        Map.of("sourceCount", Integer.toString(sourceCount)));
            } else {
                recorder.metrics().add("source.scan.failure_count", result.failures().size());
                operation.warning(
                        OperationalEventCode.SYNC_FAILED,
                        Map.of("failureCount", Integer.toString(result.failures().size())));
            }
            return result;
        } catch (RuntimeException failure) {
            operation.failure(
                    OperationalEventCode.SYNC_FAILED,
                    Map.of("errorType", failure.getClass().getSimpleName()));
            throw failure;
        }
    }

    private SourceInventoryScanResult scanInternal(
            Path workspaceRoot,
            ProjectSpecificationId projectId,
            Optional<String> sourceRevision,
            Instant capturedAt,
            Collection<Path> sourceRoots) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
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
        ScanBudget budget = new ScanBudget(policy);

        for (Path root : roots) {
            if (budget.exhausted()) break;
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
                        int depth = relativeDepth(root, directory);
                        if (depth > policy.maxDepth()) {
                            failures.add(limitFailure(
                                    workspace,
                                    directory,
                                    "source scan depth exceeds limit " + policy.maxDepth()));
                            budget.exhaust();
                            return FileVisitResult.TERMINATE;
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
                        int depth = relativeDepth(root, file);
                        if (depth > policy.maxDepth()) {
                            failures.add(limitFailure(
                                    workspace,
                                    file,
                                    "source scan depth exceeds limit " + policy.maxDepth()));
                            budget.exhaust();
                            return FileVisitResult.TERMINATE;
                        }
                        Optional<String> rejection = budget.reserve(attrs.size());
                        if (rejection.isPresent()) {
                            failures.add(limitFailure(workspace, file, rejection.orElseThrow()));
                            return FileVisitResult.TERMINATE;
                        }
                        try {
                            SourcePath sourcePath = new SourcePath(
                                    workspace.relativize(file.toAbsolutePath().normalize()).toString());
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
                                    safeMessage(exception)));
                        }
                        return budget.exhausted() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exception) {
                        failures.add(new SourceInventoryScanResult.Failure(
                                Optional.of(display(workspace, file)),
                                safeMessage(exception)));
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException | RuntimeException exception) {
                failures.add(new SourceInventoryScanResult.Failure(
                        Optional.of(display(workspace, root)),
                        safeMessage(exception)));
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

    private static int relativeDepth(Path root, Path candidate) {
        Path relative = root.toAbsolutePath().normalize().relativize(candidate.toAbsolutePath().normalize());
        return relative.toString().isEmpty() ? 0 : relative.getNameCount();
    }

    private static SourceInventoryScanResult.Failure limitFailure(Path workspace, Path path, String message) {
        return new SourceInventoryScanResult.Failure(Optional.of(display(workspace, path)), message);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static String display(Path workspace, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(workspace)
                ? workspace.relativize(normalized).toString().replace('\\', '/')
                : normalized.toString();
    }

    private static final class ScanBudget {
        private final SourceScanPolicy policy;
        private long files;
        private long aggregateBytes;
        private boolean exhausted;

        private ScanBudget(SourceScanPolicy policy) {
            this.policy = policy;
        }

        private Optional<String> reserve(long fileBytes) {
            if (fileBytes < 0) {
                exhausted = true;
                return Optional.of("source file size is negative");
            }
            if (files >= policy.maxFiles()) {
                exhausted = true;
                return Optional.of("source scan file count exceeds limit " + policy.maxFiles());
            }
            if (fileBytes > policy.maxFileBytes()) {
                exhausted = true;
                return Optional.of("source file exceeds size limit " + policy.maxFileBytes() + " bytes");
            }
            if (aggregateBytes > policy.maxAggregateBytes() - fileBytes) {
                exhausted = true;
                return Optional.of("source scan aggregate bytes exceed limit " + policy.maxAggregateBytes());
            }
            files++;
            aggregateBytes += fileBytes;
            return Optional.empty();
        }

        private boolean exhausted() {
            return exhausted;
        }

        private void exhaust() {
            exhausted = true;
        }
    }
}
