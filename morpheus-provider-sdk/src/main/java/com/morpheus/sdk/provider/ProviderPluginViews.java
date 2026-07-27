package com.morpheus.sdk.provider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Transport-safe immutable views without java.nio.Path implementation details. */
public final class ProviderPluginViews {
    private ProviderPluginViews() {
    }

    public static DiscoveryView discovery(ProviderPluginDiscoveryResult result) {
        Objects.requireNonNull(result, "result");
        return new DiscoveryView(
                result.directory().toString(),
                result.candidates().stream().map(ProviderPluginViews::candidate).toList(),
                result.diagnostics(),
                result.compatibleCount());
    }

    private static CandidateView candidate(ProviderPluginCandidate candidate) {
        return new CandidateView(
                candidate.jarPath().toString(),
                candidate.metadata(),
                candidate.status(),
                candidate.diagnostics());
    }

    public record DiscoveryView(
            String directory,
            List<CandidateView> candidates,
            List<ProviderPluginDiagnostic> diagnostics,
            long compatibleCount) {
        public DiscoveryView {
            directory = requireText(directory, "directory");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public record CandidateView(
            String jarPath,
            Optional<ProviderPluginMetadata> metadata,
            ProviderPluginStatus status,
            List<ProviderPluginDiagnostic> diagnostics) {
        public CandidateView {
            jarPath = requireText(jarPath, "jarPath");
            metadata = Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(status, "status");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
