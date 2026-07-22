package com.morpheus.application.discovery;

import com.morpheus.application.provider.ProviderSelectionRequest;
import com.morpheus.application.provider.ProviderSelectionResult;
import com.morpheus.application.provider.SpecificationProviderRegistry;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.source.SpecificationSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Entry point for deterministic workspace/provider discovery. */
public final class ProjectDiscoveryService {
    private final SpecificationProviderRegistry providerRegistry;
    private final WorkspaceRootResolver workspaceRootResolver;

    public ProjectDiscoveryService(SpecificationProviderRegistry providerRegistry) {
        this(providerRegistry, new WorkspaceRootResolver());
    }

    public ProjectDiscoveryService(
            SpecificationProviderRegistry providerRegistry,
            WorkspaceRootResolver workspaceRootResolver) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.workspaceRootResolver = Objects.requireNonNull(workspaceRootResolver, "workspaceRootResolver");
    }

    public ProjectDiscoveryResult discover(Path workspacePath, ProviderSelectionRequest request) {
        Objects.requireNonNull(workspacePath, "workspacePath");
        Objects.requireNonNull(request, "request");

        Path requestedPath = workspacePath.toAbsolutePath().normalize();
        if (!Files.isDirectory(requestedPath)) {
            Diagnostic diagnostic = Diagnostic.error(
                    DiagnosticCode.INVALID_SOURCE,
                    "The workspace path does not exist or is not a directory.",
                    Map.of("workspacePath", requestedPath.toString()));
            return new ProjectDiscoveryResult(
                    requestedPath,
                    requestedPath,
                    WorkspaceRootKind.EXPLICIT,
                    List.of(),
                    Optional.empty(),
                    List.of(),
                    List.of(diagnostic));
        }

        List<WorkspaceRootCandidate> candidates = workspaceRootResolver.candidates(requestedPath);
        WorkspaceRootCandidate explicitCandidate = candidates.getFirst();
        ProviderSelectionResult explicitSelection = providerRegistry.select(explicitCandidate.root(), request);

        if (hasRecognizedSource(explicitSelection)) {
            return result(requestedPath, explicitCandidate, explicitSelection);
        }

        if (candidates.size() > 1) {
            WorkspaceRootCandidate gitCandidate = candidates.get(1);
            ProviderSelectionResult gitSelection = providerRegistry.select(gitCandidate.root(), request);
            if (hasRecognizedSource(gitSelection)) {
                return result(requestedPath, gitCandidate, gitSelection);
            }
        }

        return result(requestedPath, explicitCandidate, explicitSelection);
    }

    private boolean hasRecognizedSource(ProviderSelectionResult selection) {
        return selection.selected().isPresent()
                || selection.evaluated().stream().anyMatch(ProviderProbeResult::recognizedSource);
    }

    private ProjectDiscoveryResult result(
            Path requestedPath,
            WorkspaceRootCandidate candidate,
            ProviderSelectionResult selection) {
        List<SpecificationSource> sources = selection.evaluated().stream()
                .flatMap(probe -> probe.sourceLocator().stream()
                        .map(locator -> new SpecificationSource(
                                probe.providerId(),
                                locator,
                                probe.schema(),
                                probe.formatVersion(),
                                probe.capabilities())))
                .sorted()
                .toList();

        return new ProjectDiscoveryResult(
                requestedPath,
                candidate.root(),
                candidate.kind(),
                sources,
                selection.selected(),
                selection.evaluated(),
                selection.diagnostics());
    }
}
