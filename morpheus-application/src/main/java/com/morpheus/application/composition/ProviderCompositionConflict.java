package com.morpheus.application.composition;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Explainable conflict between provider contributions that claim the same logical entity. */
public record ProviderCompositionConflict(
        ProviderEntityKind entityKind,
        String logicalKey,
        ProviderConflictResolution resolution,
        Optional<ProviderConflictContender> winner,
        List<ProviderConflictContender> contenders,
        String reason) {

    public ProviderCompositionConflict {
        Objects.requireNonNull(entityKind, "entityKind");
        logicalKey = requireNonBlank(logicalKey, "logicalKey");
        Objects.requireNonNull(resolution, "resolution");
        winner = Objects.requireNonNull(winner, "winner");
        contenders = Objects.requireNonNull(contenders, "contenders").stream()
                .peek(item -> Objects.requireNonNull(item, "contender"))
                .sorted(Comparator.comparingInt(ProviderConflictContender::precedence).reversed()
                        .thenComparing(item -> item.providerId().value())
                        .thenComparing(ProviderConflictContender::entityId))
                .toList();
        if (contenders.size() < 2) {
            throw new IllegalArgumentException("a provider conflict requires at least two contenders");
        }
        long distinctProviders = contenders.stream().map(ProviderConflictContender::providerId).distinct().count();
        if (distinctProviders != contenders.size()) {
            throw new IllegalArgumentException("a provider conflict cannot contain duplicate providers");
        }
        if (resolution == ProviderConflictResolution.RESOLVED_BY_PRECEDENCE) {
            ProviderConflictContender selected = winner.orElseThrow(() ->
                    new IllegalArgumentException("resolved conflict requires a winner"));
            if (!contenders.contains(selected)) {
                throw new IllegalArgumentException("winner must be one of the contenders");
            }
        } else if (winner.isPresent()) {
            throw new IllegalArgumentException("unresolved conflict cannot expose a winner");
        }
        reason = requireNonBlank(reason, "reason");
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
