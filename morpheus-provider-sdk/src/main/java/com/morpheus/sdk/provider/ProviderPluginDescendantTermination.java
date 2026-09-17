package com.morpheus.sdk.provider;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Terminates the process subtree below a given root while that root is still alive.
 *
 * <p>This is the only moment at which the subtree is reliably enumerable: once the root exits, the operating system
 * re-parents its children and no portable API can still attribute them to it. Sampling {@code descendants()} from
 * outside therefore cannot guarantee cleanup, because a process spawned and orphaned between two samples is never
 * seen.
 */
final class ProviderPluginDescendantTermination {
    /**
     * Minimum window for observing that a forcible kill landed.
     *
     * <p>A grace period is an offer: it is how long a process is given to leave on its own. What follows
     * {@code destroyForcibly()} is not that -- the kill is not negotiable, and the only thing left to wait for is
     * the operating system and the JDK process reaper making the exit observable. Reusing the caller's grace for
     * it meant {@link #terminate(List, Duration)} reported failure for a subtree it had already killed: a child
     * JVM needs single-digit milliseconds to become observable, measured at up to 4.7 ms on an idle Linux host,
     * so every caller passing a shorter grace got {@code false} for a process that was gone.</p>
     *
     * <p>This is a ceiling, not a cost: the wait ends as soon as the handles exit. It is only spent on a process
     * that survives {@code SIGKILL}, which is exactly the case a caller needs reported truthfully.</p>
     */
    private static final Duration FORCIBLE_SETTLE = Duration.ofSeconds(2);

    private ProviderPluginDescendantTermination() {
    }

    /** Snapshots the descendants of {@code root} and terminates them, escalating to a forcible kill if needed. */
    static void reapDescendantsOf(ProcessHandle root, Duration grace) {
        Objects.requireNonNull(root, "root");
        terminate(root.descendants().toList(), grace);
        // Re-snapshot: a descendant may have spawned another one while the graceful signal was in flight.
        List<ProcessHandle> remaining = root.descendants().toList();
        if (!remaining.isEmpty()) {
            terminate(remaining, grace);
        }
    }

    /**
     * Signals every live handle gracefully, then forcibly if any survives the grace period.
     *
     * <p>The graceful wait honours {@code grace}; the wait after the forcible signal does not, because it is
     * observation latency rather than an offer -- see {@link #FORCIBLE_SETTLE}.</p>
     *
     * @return true when no handle is left alive
     */
    static boolean terminate(List<ProcessHandle> handles, Duration grace) {
        Objects.requireNonNull(handles, "handles");
        Objects.requireNonNull(grace, "grace");
        if (handles.isEmpty()) {
            return true;
        }
        signal(handles, false);
        if (awaitExit(handles, grace)) {
            return true;
        }
        signal(handles, true);
        return awaitExit(handles, longerOf(grace, FORCIBLE_SETTLE));
    }

    private static Duration longerOf(Duration left, Duration right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private static void signal(List<ProcessHandle> handles, boolean forcibly) {
        for (ProcessHandle handle : handles) {
            if (!handle.isAlive()) {
                continue;
            }
            try {
                if (forcibly) {
                    handle.destroyForcibly();
                } else {
                    handle.destroy();
                }
            } catch (RuntimeException vanished) {
                // The descendant exited between the liveness check and the signal.
            }
        }
    }

    private static boolean awaitExit(List<ProcessHandle> handles, Duration grace) {
        CompletableFuture<?>[] exits = handles.stream()
                .map(ProcessHandle::onExit)
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(exits).get(grace.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return noneAlive(handles);
        } catch (ExecutionException | TimeoutException notTerminated) {
            return noneAlive(handles);
        }
    }

    private static boolean noneAlive(List<ProcessHandle> handles) {
        return handles.stream().noneMatch(ProcessHandle::isAlive);
    }
}
