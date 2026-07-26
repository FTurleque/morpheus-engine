package com.morpheus.application.composition;

import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Snapshot-scoped composition metadata, independent from provider-specific source formats. */
public record CompositionSnapshotState(
        KnowledgeSnapshotId snapshotId,
        ProviderId primaryProviderId,
        List<CompositionProviderState> providers,
        List<CompositionConflict> conflicts) {

    public CompositionSnapshotState {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(primaryProviderId, "primaryProviderId");
        providers = Objects.requireNonNull(providers, "providers").stream()
                .sorted(Comparator.comparingInt(CompositionProviderState::priority).reversed()
                        .thenComparing(CompositionProviderState::providerId))
                .toList();
        conflicts = Objects.requireNonNull(conflicts, "conflicts").stream()
                .sorted(Comparator.comparing((CompositionConflict item) -> item.entityType().name())
                        .thenComparing(CompositionConflict::logicalKey)
                        .thenComparing(CompositionConflict::field))
                .toList();
        if (providers.stream().noneMatch(item -> item.providerId().equals(primaryProviderId))) {
            throw new IllegalArgumentException("primary provider must be present in providers");
        }
    }

    public static CompositionSnapshotState from(
            KnowledgeSnapshotId snapshotId,
            MultiProviderCompositionResult result) {
        Objects.requireNonNull(result, "result");
        return new CompositionSnapshotState(
                snapshotId,
                result.primaryProviderId(),
                result.contributions().stream().map(CompositionProviderState::from).toList(),
                result.conflicts());
    }
}
