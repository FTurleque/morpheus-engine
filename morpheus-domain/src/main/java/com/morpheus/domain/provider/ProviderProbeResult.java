package com.morpheus.domain.provider;

import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.source.SourceLocator;

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
        Optional<SourceLocator> sourceLocator,
        ProviderCapabilitySet capabilities,
        boolean remote,
        List<Diagnostic> diagnostics) {

    public ProviderProbeResult {
        Objects.requireNonNull(providerId, "providerId");
        providerVersion = Objects.requireNonNull(providerVersion, "providerVersion").trim();
        Objects.requireNonNull(status, "status");
        schema = Objects.requireNonNull(schema, "schema");
        formatVersion = Objects.requireNonNull(formatVersion, "formatVersion");
        sourceLocator = Objects.requireNonNull(sourceLocator, "sourceLocator");
        Objects.requireNonNull(capabilities, "capabilities");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    /** Compatibility constructor for probes that do not yet expose a source locator. */
    public ProviderProbeResult(
            ProviderId providerId,
            String providerVersion,
            ProviderProbeStatus status,
            Optional<String> schema,
            Optional<String> formatVersion,
            ProviderCapabilitySet capabilities,
            boolean remote,
            List<Diagnostic> diagnostics) {
        this(
                providerId,
                providerVersion,
                status,
                schema,
                formatVersion,
                Optional.empty(),
                capabilities,
                remote,
                diagnostics);
    }

    public boolean supported() {
        return status == ProviderProbeStatus.SUPPORTED;
    }

    /** True when the provider recognized a concrete source, even if it cannot use it. */
    public boolean recognizedSource() {
        return sourceLocator.isPresent() || status == ProviderProbeStatus.INVALID || !diagnostics.isEmpty();
    }
}
