package com.morpheus.sdk.provider;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Result of an explicit plugin-directory inspection. */
public record ProviderPluginDiscoveryResult(
        Path directory,
        List<ProviderPluginCandidate> candidates,
        List<ProviderPluginDiagnostic> diagnostics) {

    public ProviderPluginDiscoveryResult {
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public long compatibleCount() {
        return candidates.stream().filter(ProviderPluginCandidate::compatible).count();
    }
}
