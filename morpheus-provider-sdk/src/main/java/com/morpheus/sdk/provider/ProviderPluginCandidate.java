package com.morpheus.sdk.provider;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One inspected plugin JAR. The candidate contains metadata only; no plugin code has been loaded. */
public record ProviderPluginCandidate(
        Path jarPath,
        Optional<ProviderPluginMetadata> metadata,
        ProviderPluginStatus status,
        List<ProviderPluginDiagnostic> diagnostics) {

    public ProviderPluginCandidate {
        jarPath = Objects.requireNonNull(jarPath, "jarPath").toAbsolutePath().normalize();
        metadata = Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(status, "status");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (status == ProviderPluginStatus.COMPATIBLE && metadata.isEmpty()) {
            throw new IllegalArgumentException("a compatible plugin candidate requires metadata");
        }
    }

    public boolean compatible() {
        return status == ProviderPluginStatus.COMPATIBLE;
    }
}
