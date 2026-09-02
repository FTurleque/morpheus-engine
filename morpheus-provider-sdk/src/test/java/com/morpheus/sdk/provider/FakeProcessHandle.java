package com.morpheus.sdk.provider;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Controllable {@link ProcessHandle} used to drive termination branches that a real process cannot reach portably.
 *
 * <p>{@code destroy()} maps to {@code TerminateProcess} on Windows and to {@code SIGTERM} on POSIX, so "a process
 * that survives graceful termination" and "an exit signal that fails" are not reproducible with real processes on
 * both platforms. The real-process tests cover the integration behaviour; this fake covers the branches.
 */
final class FakeProcessHandle implements ProcessHandle {
    private final long pid;
    private final CompletableFuture<ProcessHandle> exit = new CompletableFuture<>();
    private volatile boolean alive = true;
    private boolean survivesNormalTermination;
    private boolean signallingThrows;
    private final Deque<List<ProcessHandle>> descendantSnapshots = new ArrayDeque<>();

    FakeProcessHandle(long pid) {
        this.pid = pid;
    }

    FakeProcessHandle survivingNormalTermination() {
        this.survivesNormalTermination = true;
        return this;
    }

    FakeProcessHandle throwingOnSignal() {
        this.signallingThrows = true;
        return this;
    }

    FakeProcessHandle alreadyExited() {
        this.alive = false;
        exit.complete(this);
        return this;
    }

    FakeProcessHandle withFailedExitSignal() {
        this.alive = false;
        exit.completeExceptionally(new IllegalStateException("exit notification unavailable"));
        return this;
    }

    /** Queues one {@code descendants()} result per call, so a handle appearing between snapshots can be modelled. */
    FakeProcessHandle withDescendantSnapshots(List<ProcessHandle>... snapshots) {
        descendantSnapshots.addAll(List.of(snapshots));
        return this;
    }

    @Override
    public long pid() {
        return pid;
    }

    @Override
    public Optional<ProcessHandle> parent() {
        return Optional.empty();
    }

    @Override
    public Stream<ProcessHandle> children() {
        return Stream.empty();
    }

    @Override
    public Stream<ProcessHandle> descendants() {
        List<ProcessHandle> snapshot = descendantSnapshots.poll();
        return snapshot == null ? Stream.empty() : snapshot.stream();
    }

    @Override
    public Info info() {
        throw new UnsupportedOperationException("process information is never read during termination");
    }

    @Override
    public CompletableFuture<ProcessHandle> onExit() {
        return exit;
    }

    @Override
    public boolean supportsNormalTermination() {
        return !survivesNormalTermination;
    }

    @Override
    public boolean destroy() {
        if (signallingThrows) {
            throw new IllegalStateException("process vanished before it could be signalled");
        }
        if (survivesNormalTermination) {
            return false;
        }
        return die();
    }

    @Override
    public boolean destroyForcibly() {
        if (signallingThrows) {
            throw new IllegalStateException("process vanished before it could be signalled");
        }
        return die();
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public int compareTo(ProcessHandle other) {
        return Long.compare(pid, other.pid());
    }

    private boolean die() {
        alive = false;
        exit.complete(this);
        return true;
    }
}
