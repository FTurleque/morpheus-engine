package com.morpheus.application.discovery;

import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.source.SpecificationSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProjectDiscoveryResult(
        Path requestedPath,
        Path workspaceRoot,
        WorkspaceRootKind workspaceRootKind,
        List<SpecificationSource> sources,
        Optional<ProviderProbeResult> selectedProvider,
        List<ProviderProbeResult> probes,
        List<Diagnostic> diagnostics) {

    public ProjectDiscoveryResult {
        requestedPath = Objects.requireNonNull(requestedPath, "requestedPath").toAbsolutePath().normalize();
        workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        Objects.requireNonNull(workspaceRootKind, "workspaceRootKind");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        selectedProvider = Objects.requireNonNull(selectedProvider, "selectedProvider");
        probes = List.copyOf(Objects.requireNonNull(probes, "probes"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    /** Compatibility constructor for callers that only know one workspace root. */
    public ProjectDiscoveryResult(
            Path workspaceRoot,
            Optional<ProviderProbeResult> selectedProvider,
            List<ProviderProbeResult> probes,
            List<Diagnostic> diagnostics) {
        this(
                workspaceRoot,
                workspaceRoot,
                WorkspaceRootKind.EXPLICIT,
                List.of(),
                selectedProvider,
                probes,
                diagnostics);
    }

    public boolean resolvedFromGitAncestor() {
        return workspaceRootKind == WorkspaceRootKind.GIT_ANCESTOR;
    }
}
