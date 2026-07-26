package com.morpheus.application.composition;

import com.morpheus.domain.provider.ProviderId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Explicit divergence between provider observations sharing one logical key. */
public record CompositionConflict(
        CompositionEntityType entityType,
        String logicalKey,
        String field,
        List<CompositionCandidate> candidates,
        CompositionResolution resolution,
        Optional<ProviderId> selectedProviderId,
        String reason) {

    public CompositionConflict {
        Objects.requireNonNull(entityType, "entityType");
        logicalKey = requireNonBlank(logicalKey, "logicalKey");
        field = requireNonBlank(field, "field");
        candidates = Objects.requireNonNull(candidates, "candidates").stream()
                .sorted(Comparator.comparingInt(CompositionCandidate::priority).reversed()
                        .thenComparing(CompositionCandidate::providerId))
                .toList();
        if (candidates.size() < 2) {
            throw new IllegalArgumentException("a composition conflict requires at least two candidates");
        }
        Objects.requireNonNull(resolution, "resolution");
        selectedProviderId = Objects.requireNonNull(selectedProviderId, "selectedProviderId");
        reason = requireNonBlank(reason, "reason");
        if (resolution == CompositionResolution.SELECTED_BY_PRECEDENCE && selectedProviderId.isEmpty()) {
            throw new IllegalArgumentException("precedence resolution requires selected provider");
        }
        if (resolution == CompositionResolution.UNRESOLVED && selectedProviderId.isPresent()) {
            throw new IllegalArgumentException("unresolved conflict cannot select a provider");
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
