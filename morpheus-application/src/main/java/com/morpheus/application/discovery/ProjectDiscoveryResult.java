package com.morpheus.application.discovery;

import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.provider.ProviderProbeResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProjectDiscoveryResult(
        Path workspaceRoot,
        Optional<ProviderProbeResult> selectedProvider,
        List<ProviderProbeResult> probes,
        List<Diagnostic> diagnostics) {

    public ProjectDiscoveryResult {
        workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        selectedProvider = Objects.requireNonNull(selectedProvider, "selectedProvider");
        probes = List.copyOf(Objects.requireNonNull(probes, "probes"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
