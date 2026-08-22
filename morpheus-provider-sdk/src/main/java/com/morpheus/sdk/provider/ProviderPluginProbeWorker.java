package com.morpheus.sdk.provider;

import com.morpheus.domain.provider.ProviderProbeResult;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** JVM entry point used only by {@link ProviderPluginProbeProcess}. */
public final class ProviderPluginProbeWorker {
    private static final Duration DESCENDANT_GRACE = Duration.ofMillis(250);

    private ProviderPluginProbeWorker() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("expected: JAR WORKSPACE SHA256 RESULT_FILE");
        }
        Path jar = Path.of(args[0]).toAbsolutePath().normalize();
        Path workspace = Path.of(args[1]).toAbsolutePath().normalize();
        String sha256 = args[2];
        Path resultFile = Path.of(args[3]).toAbsolutePath().normalize();

        try {
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
        } finally {
            terminateDescendants();
        }
    }

    /** Prevents a normally returning plugin probe from intentionally or accidentally leaving helper processes behind. */
    private static void terminateDescendants() {
        List<ProcessHandle> descendants = ProcessHandle.current().descendants().toList();
        for (ProcessHandle descendant : descendants) {
            if (descendant.isAlive()) descendant.destroy();
        }
        long deadline = System.nanoTime() + DESCENDANT_GRACE.toNanos();
        while (descendants.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        for (ProcessHandle descendant : descendants) {
            if (descendant.isAlive()) descendant.destroyForcibly();
        }
    }
}
