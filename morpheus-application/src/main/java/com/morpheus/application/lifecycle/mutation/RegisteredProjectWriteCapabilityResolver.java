package com.morpheus.application.lifecycle.mutation;

import com.morpheus.application.provider.SpecificationProviderRegistry;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderProbeResult;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Resolves WRITE_CHANGE from real provider probes for the registered project root. */
public final class RegisteredProjectWriteCapabilityResolver implements ChangeWriteCapabilityResolver {
    private final SpecificationKnowledgeStore projects;
    private final SpecificationProviderRegistry providers;

    public RegisteredProjectWriteCapabilityResolver(
            SpecificationKnowledgeStore projects,
            SpecificationProviderRegistry providers) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.providers = Objects.requireNonNull(providers, "providers");
    }

    @Override
    public ChangeWriteCapabilityObservation resolve(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        var project = projects.findProject(projectId).orElse(null);
        if (project == null) {
            return ChangeWriteCapabilityObservation.denied("Project is not registered");
        }
        var root = project.rootLocator();
        if (!"file".equals(root.scheme())) {
            return ChangeWriteCapabilityObservation.denied(
                    "WRITE_CHANGE capability cannot be probed for non-file project root: " + root.scheme());
        }

        List<ProviderProbeResult> candidates = providers.probeAll(Path.of(root.value())).stream()
                .filter(ProviderProbeResult::supported)
                .filter(probe -> probe.capabilities().contains(ProviderCapability.WRITE_CHANGE))
                .sorted(Comparator.comparing(ProviderProbeResult::providerId))
                .toList();
        if (candidates.isEmpty()) {
            return ChangeWriteCapabilityObservation.denied(
                    "No supported provider explicitly exposes WRITE_CHANGE for this project");
        }
        if (candidates.size() > 1) {
            return ChangeWriteCapabilityObservation.denied(
                    "Multiple providers expose WRITE_CHANGE; explicit provider selection is required");
        }
        ProviderProbeResult selected = candidates.getFirst();
        return ChangeWriteCapabilityObservation.allowed(
                selected.providerId(),
                "Provider explicitly exposes WRITE_CHANGE: " + selected.providerId());
    }
}
