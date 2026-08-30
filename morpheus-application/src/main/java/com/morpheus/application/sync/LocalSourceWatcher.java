package com.morpheus.application.sync;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Recursive local watcher used only as a rescan trigger; SHA-256 inventory scanning remains authoritative. */
public final class LocalSourceWatcher implements AutoCloseable {
    private final Path workspaceRoot;
    private final WatchService watchService;
    private final SourceScanPolicy policy;
    private final Map<WatchKey, Path> directories = new HashMap<>();
    private final Set<Path> registeredDirectories = new HashSet<>();
    private boolean closed;

    public LocalSourceWatcher(Path workspaceRoot, Collection<Path> watchedRoots) throws IOException {
        this(workspaceRoot, watchedRoots, SourceScanPolicy.safeDefaults());
    }

    public LocalSourceWatcher(
            Path workspaceRoot,
            Collection<Path> watchedRoots,
            SourceScanPolicy policy) throws IOException {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(watchedRoots, "watchedRoots");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.watchService = FileSystems.getDefault().newWatchService();
        Collection<Path> roots = watchedRoots.isEmpty() ? List.of(this.workspaceRoot) : watchedRoots;
        try {
            for (Path root : roots) {
                Path resolved = resolveWithin(this.workspaceRoot, root);
                WorkspacePathBoundary.requireContained(this.workspaceRoot, resolved);
                if (!Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(resolved)) {
                    throw new IOException("watched root is not a real directory: " + root);
                }
                registerRecursively(resolved);
            }
        } catch (IOException | RuntimeException exception) {
            try {
                watchService.close();
            } catch (IOException ignored) {
                // Preserve original constructor failure.
            }
            throw exception;
        }
    }

    public synchronized List<SourceWatchSignal> poll(Duration timeout) throws InterruptedException {
        ensureOpen();
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be >= 0");
        }

        WatchKey key = watchService.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (key == null) {
            return List.of();
        }

        List<SourceWatchSignal> signals = new ArrayList<>();
        do {
            consume(key, signals);
            key = watchService.poll();
        } while (key != null);

        return signals.stream().distinct().sorted().toList();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            watchService.close();
            closed = true;
            directories.clear();
            registeredDirectories.clear();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot close local source watcher", exception);
        }
    }

    private void consume(WatchKey key, List<SourceWatchSignal> signals) {
        Path directory = directories.get(key);
        if (directory == null) {
            signals.add(SourceWatchSignal.overflow());
            key.reset();
            return;
        }

        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                signals.add(SourceWatchSignal.overflow());
                continue;
            }
            Object context = event.context();
            if (!(context instanceof Path relative)) {
                signals.add(SourceWatchSignal.overflow());
                continue;
            }
            Path absolute = directory.resolve(relative).toAbsolutePath().normalize();
            if (!absolute.startsWith(workspaceRoot)) {
                signals.add(SourceWatchSignal.overflow());
                continue;
            }
            try {
                WorkspacePathBoundary.requireContained(workspaceRoot, directory);
                WorkspacePathBoundary.requireContained(workspaceRoot, absolute);
                SourcePath sourcePath = new SourcePath(workspaceRoot.relativize(absolute).toString());
                signals.add(new SourceWatchSignal(toKind(kind), java.util.Optional.of(sourcePath)));
                if (kind == StandardWatchEventKinds.ENTRY_CREATE
                        && Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(absolute)) {
                    registerRecursively(absolute);
                }
            } catch (IOException | RuntimeException exception) {
                signals.add(SourceWatchSignal.overflow());
            }
        }

        if (!key.reset()) {
            directories.remove(key);
            registeredDirectories.remove(directory);
            signals.add(SourceWatchSignal.overflow());
        }
    }

    private void registerRecursively(Path root) throws IOException {
        WorkspacePathBoundary.requireContained(workspaceRoot, root);
        int rootDepth = depth(root);
        if (rootDepth > policy.maxDepth()) {
            throw new IOException("watched root exceeds source scan depth budget: " + root);
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                WorkspacePathBoundary.requireContained(workspaceRoot, directory);
                if (Files.isSymbolicLink(directory)
                        || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                int directoryDepth = depth(directory);
                if (directoryDepth > policy.maxDepth()) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!directory.equals(root) && policy.ignoresDirectory(directory)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                registerDirectory(directory);
                return directoryDepth == policy.maxDepth()
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        WorkspacePathBoundary.requireContained(workspaceRoot, normalized);
        if (registeredDirectories.contains(normalized)) {
            return;
        }
        if (registeredDirectories.size() >= policy.maxDirectories()) {
            throw new IOException("watched directory budget exceeds " + policy.maxDirectories());
        }
        WatchKey key = normalized.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        directories.put(key, normalized);
        registeredDirectories.add(normalized);
    }

    private int depth(Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        if (normalized.equals(workspaceRoot)) {
            return 0;
        }
        return workspaceRoot.relativize(normalized).getNameCount();
    }

    private SourceWatchSignal.Kind toKind(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return SourceWatchSignal.Kind.CREATE;
        }
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return SourceWatchSignal.Kind.MODIFY;
        }
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return SourceWatchSignal.Kind.DELETE;
        }
        return SourceWatchSignal.Kind.OVERFLOW;
    }

    private Path resolveWithin(Path workspace, Path root) {
        Objects.requireNonNull(root, "watchedRoots item");
        Path resolved = (root.isAbsolute() ? root : workspace.resolve(root)).toAbsolutePath().normalize();
        if (!resolved.startsWith(workspace)) {
            throw new IllegalArgumentException("watched root escapes workspace: " + root);
        }
        return resolved;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("local source watcher is closed");
        }
    }
}
