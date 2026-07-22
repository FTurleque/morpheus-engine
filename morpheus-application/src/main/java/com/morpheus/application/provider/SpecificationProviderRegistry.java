package com.morpheus.application.provider;

import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Registry of available specification providers. No normalization logic belongs here. */
public final class SpecificationProviderRegistry {
    private final List<SpecificationProvider> providers;
    private final ProviderSelectionPolicy selectionPolicy;

    public SpecificationProviderRegistry(
            Collection<? extends SpecificationProvider> providers,
            ProviderSelectionPolicy selectionPolicy) {
        Objects.requireNonNull(providers, "providers");
        this.selectionPolicy = Objects.requireNonNull(selectionPolicy, "selectionPolicy");

        List<SpecificationProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator.comparing(SpecificationProvider::id));
        rejectDuplicateIds(sorted);
        this.providers = List.copyOf(sorted);
    }

    public List<ProviderProbeResult> probeAll(Path workspaceRoot) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        return providers.stream().map(provider -> provider.probe(workspaceRoot)).toList();
    }

    public ProviderSelectionResult select(Path workspaceRoot, ProviderSelectionRequest request) {
        return selectionPolicy.select(probeAll(workspaceRoot), request);
    }

    public List<ProviderId> providerIds() {
        return providers.stream().map(SpecificationProvider::id).toList();
    }

    private void rejectDuplicateIds(List<SpecificationProvider> providers) {
        Set<ProviderId> seen = new HashSet<>();
        for (SpecificationProvider provider : providers) {
            if (!seen.add(provider.id())) {
                throw new IllegalArgumentException("duplicate provider id: " + provider.id());
            }
        }
    }
}
