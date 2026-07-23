package com.morpheus.application.sync;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Recursive local watcher used only as a rescan trigger; SHA-256 inventory scanning remains authoritative. */
public final class LocalSourceWatcher implements AutoCloseable {
    private final Path workspaceRoot;
    private final WatchService watchService;
    private final Map<WatchKey, Path> directories = new HashMap<>();
    private boolean closed;

    public LocalSourceWatcher(Path workspaceRoot, Collection<Path> watchedRoots) throws IOException {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(watchedRoots, "watchedRoots");
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.watchService = FileSystems.getDefault().newWatchService();
        Collection<Path> roots = watchedRoots.isEmpty() ? List.of(this.workspaceRoot) : watchedRoots;
        try {
            for (Path root : roots) {
                Path resolved = resolveWithin(this.workspaceRoot, root);
                if (!Files.isDirectory(resolved)) {
                    throw new IOException("watched root is not a directory: " + root);
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
                SourcePath sourcePath = new SourcePath(workspaceRoot.relativize(absolute).toString());
                signals.add(new SourceWatchSignal(toKind(kind), java.util.Optional.of(sourcePath)));
                if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(absolute)) {
                    registerRecursively(absolute);
                }
            } catch (IOException | RuntimeException exception) {
                signals.add(SourceWatchSignal.overflow());
            }
        }

        if (!key.reset()) {
            directories.remove(key);
            signals.add(SourceWatchSignal.overflow());
        }
    }

    private void registerRecursively(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            for (Path directory : stream.filter(Files::isDirectory).sorted().toList()) {
                WatchKey key = directory.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                directories.put(key, directory);
            }
        }
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
