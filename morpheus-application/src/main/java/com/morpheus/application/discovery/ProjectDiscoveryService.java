package com.morpheus.application.discovery;

import com.morpheus.application.provider.ProviderSelectionRequest;
import com.morpheus.application.provider.ProviderSelectionResult;
import com.morpheus.application.provider.SpecificationProviderRegistry;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Entry point for deterministic workspace/provider discovery. */
public final class ProjectDiscoveryService {
    private final SpecificationProviderRegistry providerRegistry;

    public ProjectDiscoveryService(SpecificationProviderRegistry providerRegistry) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
    }

    public ProjectDiscoveryResult discover(Path workspaceRoot, ProviderSelectionRequest request) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(request, "request");

        Path normalizedRoot = workspaceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            Diagnostic diagnostic = Diagnostic.error(
                    DiagnosticCode.INVALID_SOURCE,
                    "The workspace root does not exist or is not a directory.",
                    Map.of("workspaceRoot", normalizedRoot.toString()));
            return new ProjectDiscoveryResult(
                    normalizedRoot,
                    Optional.empty(),
                    List.of(),
                    List.of(diagnostic));
        }

        ProviderSelectionResult selection = providerRegistry.select(normalizedRoot, request);
        return new ProjectDiscoveryResult(
                normalizedRoot,
                selection.selected(),
                selection.evaluated(),
                selection.diagnostics());
    }
}
