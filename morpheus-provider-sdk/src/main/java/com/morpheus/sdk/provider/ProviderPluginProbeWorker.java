package com.morpheus.sdk.provider;

import com.morpheus.domain.provider.ProviderProbeResult;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * JVM entry point used only by {@link ProviderPluginProbeProcess}.
 *
 * <p>The worker reaps its own process subtree before it exits. This is the only place where that subtree is reliably
 * enumerable: once this JVM dies the operating system re-parents whatever the plugin spawned, and no portable API can
 * still attribute those processes to it. Leaving cleanup to the parent alone made termination depend on the parent
 * having sampled {@code descendants()} during the window between the spawn and this JVM exiting.
 */
public final class ProviderPluginProbeWorker {
    // Bounded twice (graceful then forcible) and spent inside the parent's probe budget, so it stays small.
    private static final Duration DESCENDANT_TERMINATION_GRACE = Duration.ofSeconds(1);

    private ProviderPluginProbeWorker() {
    }

    public static void main(String[] args) throws Exception {
        // Covers the parent's graceful destroy() path, where the body below never reaches its finally block.
        Runtime.getRuntime().addShutdownHook(
                new Thread(ProviderPluginProbeWorker::reapDescendants, "morpheus-probe-descendant-reaper"));
        try {
            runProbe(args);
        } finally {
            reapDescendants();
        }
    }

    private static void runProbe(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("expected: JAR WORKSPACE SHA256 RESULT_FILE");
        }
        Path jar = Path.of(args[0]).toAbsolutePath().normalize();
        Path workspace = Path.of(args[1]).toAbsolutePath().normalize();
        String sha256 = args[2];
        Path resultFile = Path.of(args[3]).toAbsolutePath().normalize();

        ProviderPluginCandidate candidate = new ProviderPluginDiscovery()
                .discover(Objects.requireNonNull(jar.getParent(), "plugin JAR parent"))
                .candidates().stream()
                .filter(item -> item.jarPath().equals(jar))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("isolated provider plugin candidate was not rediscovered"));
        if (!candidate.compatible()) {
            throw new IllegalStateException("isolated provider plugin candidate is not compatible");
        }

        try (ProviderPluginActivation activation = new ProviderPluginActivator().activate(candidate, sha256)) {
            ProviderProbeResult result = Objects.requireNonNull(
                    activation.provider().probe(workspace),
                    "provider probe result");
            ProviderProbeResultCodec.write(resultFile, result);
        }
    }

    /**
     * Terminates everything this JVM still has below it. Idempotent, bounded, and deliberately silent: the probe
     * outcome is already durable by the time this runs, and the parent retains its own observed-tree cleanup.
     */
    private static void reapDescendants() {
        List<ProcessHandle> descendants = ProcessHandle.current().descendants().toList();
        if (descendants.isEmpty()) {
            return;
        }
        signal(descendants, false);
        if (awaitExit(descendants)) {
            return;
        }
        // Re-snapshot: a descendant may have spawned another one while the graceful signal was in flight.
        List<ProcessHandle> remaining = ProcessHandle.current().descendants().toList();
        signal(remaining, true);
        awaitExit(remaining);
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

    private static boolean awaitExit(List<ProcessHandle> handles) {
        CompletableFuture<?>[] exits = handles.stream()
                .map(ProcessHandle::onExit)
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(exits).get(DESCENDANT_TERMINATION_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return handles.stream().noneMatch(ProcessHandle::isAlive);
        } catch (ExecutionException | TimeoutException notTerminated) {
            return handles.stream().noneMatch(ProcessHandle::isAlive);
        }
    }
}
