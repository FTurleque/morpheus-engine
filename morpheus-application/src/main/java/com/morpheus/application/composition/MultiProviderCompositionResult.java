package com.morpheus.application.composition;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.provider.ProviderId;

import java.util.List;
import java.util.Objects;

/** Deterministic composed graph plus the complete explanation of provider participation and conflicts. */
public record MultiProviderCompositionResult(
        ProviderId primaryProviderId,
        NormalizedProjectContent content,
        List<ProviderContribution> contributions,
        List<CompositionConflict> conflicts,
        List<Diagnostic> diagnostics) {

    public MultiProviderCompositionResult {
        Objects.requireNonNull(primaryProviderId, "primaryProviderId");
        Objects.requireNonNull(content, "content");
        contributions = List.copyOf(Objects.requireNonNull(contributions, "contributions"));
        conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (contributions.stream().noneMatch(item -> item.providerId().equals(primaryProviderId))) {
            throw new IllegalArgumentException("primary provider is not part of the composition");
        }
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
}
