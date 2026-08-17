package com.morpheus.sdk.provider;

import com.morpheus.domain.provider.ProviderProbeResult;

import java.nio.file.Path;
import java.util.Objects;

/** JVM entry point used only by {@link ProviderPluginProbeProcess}. */
public final class ProviderPluginProbeWorker {
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
}
