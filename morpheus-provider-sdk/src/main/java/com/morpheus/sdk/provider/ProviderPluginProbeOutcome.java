package com.morpheus.sdk.provider;

import com.morpheus.domain.provider.ProviderProbeResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Outcome of explicit activation + probe. Plugin failures are represented as diagnostics instead of escaping. */
public record ProviderPluginProbeOutcome(
        String pluginId,
        String jarPath,
        Optional<ProviderPluginMetadata> metadata,
        Optional<ProviderProbeResult> probe,
        List<ProviderPluginDiagnostic> diagnostics) {

    public ProviderPluginProbeOutcome {
        pluginId = requireText(pluginId, "pluginId");
        jarPath = jarPath == null ? "" : jarPath;
        metadata = Objects.requireNonNull(metadata, "metadata");
        probe = Objects.requireNonNull(probe, "probe");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public boolean success() {
        return probe.isPresent();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
