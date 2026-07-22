package com.morpheus.domain.provider;

import com.morpheus.domain.diagnostic.Diagnostic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable result of probing one workspace/source with one provider. */
public record ProviderProbeResult(
        ProviderId providerId,
        String providerVersion,
        ProviderProbeStatus status,
        Optional<String> schema,
        Optional<String> formatVersion,
        ProviderCapabilitySet capabilities,
        boolean remote,
        List<Diagnostic> diagnostics) {

    public ProviderProbeResult {
        Objects.requireNonNull(providerId, "providerId");
        providerVersion = Objects.requireNonNull(providerVersion, "providerVersion").trim();
        Objects.requireNonNull(status, "status");
        schema = Objects.requireNonNull(schema, "schema");
        formatVersion = Objects.requireNonNull(formatVersion, "formatVersion");
        Objects.requireNonNull(capabilities, "capabilities");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public boolean supported() {
        return status == ProviderProbeStatus.SUPPORTED;
    }
}
