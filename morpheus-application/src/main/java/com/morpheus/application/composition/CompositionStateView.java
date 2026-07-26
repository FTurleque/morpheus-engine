package com.morpheus.application.composition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** JSON-safe public projection of snapshot-scoped multi-provider composition metadata. */
public record CompositionStateView(
        String snapshotId,
        String primaryProviderId,
        List<ProviderView> providers,
        List<ConflictView> conflicts) {

    public CompositionStateView {
        snapshotId = requireNonBlank(snapshotId, "snapshotId");
        primaryProviderId = requireNonBlank(primaryProviderId, "primaryProviderId");
        providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
    }

    public static CompositionStateView from(CompositionSnapshotState state) {
        Objects.requireNonNull(state, "state");
        return new CompositionStateView(
                state.snapshotId().toString(),
                state.primaryProviderId().value(),
                state.providers().stream().map(ProviderView::from).toList(),
                state.conflicts().stream().map(ConflictView::from).toList());
    }

    public record ProviderView(
            String providerId,
            int priority,
            boolean required,
            boolean available,
            int diagnosticCount) {
        static ProviderView from(CompositionProviderState state) {
            return new ProviderView(
                    state.providerId().value(),
                    state.priority(),
                    state.required(),
                    state.available(),
                    state.diagnosticCount());
        }
    }

    public record ConflictView(
            String entityType,
            String logicalKey,
            String field,
            String resolution,
            Optional<String> selectedProviderId,
            String reason,
            List<CandidateView> candidates) {
        public ConflictView {
            selectedProviderId = Objects.requireNonNull(selectedProviderId, "selectedProviderId");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }

        static ConflictView from(CompositionConflict conflict) {
            return new ConflictView(
                    conflict.entityType().name(),
                    conflict.logicalKey(),
                    conflict.field(),
                    conflict.resolution().name(),
                    conflict.selectedProviderId().map(item -> item.value()),
                    conflict.reason(),
                    conflict.candidates().stream().map(CandidateView::from).toList());
        }
    }

    public record CandidateView(
            String providerId,
            int priority,
            String value,
            String source,
            String evidenceId) {
        static CandidateView from(CompositionCandidate candidate) {
            return new CandidateView(
                    candidate.providerId().value(),
                    candidate.priority(),
                    candidate.value(),
                    candidate.source(),
                    candidate.evidenceId());
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
