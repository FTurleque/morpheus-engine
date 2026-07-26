package com.morpheus.application.composition;

import com.morpheus.domain.provider.ProviderId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable snapshot-worthy explanation of a multi-provider composition pass. */
public record ProviderCompositionReport(
        List<ProviderContribution> contributions,
        List<ProviderCompositionConflict> conflicts) {

    public ProviderCompositionReport {
        contributions = canonicalContributions(contributions);
        conflicts = canonicalConflicts(conflicts);
    }

    public static ProviderCompositionReport empty() {
        return new ProviderCompositionReport(List.of(), List.of());
    }

    public boolean hasUnresolvedConflicts() {
        return conflicts.stream().anyMatch(conflict ->
                conflict.resolution() == ProviderConflictResolution.UNRESOLVED_EQUAL_PRECEDENCE);
    }

    public int providerCount() {
        return contributions.size();
    }

    private static List<ProviderContribution> canonicalContributions(List<ProviderContribution> source) {
        Objects.requireNonNull(source, "contributions");
        List<ProviderContribution> copy = new ArrayList<>(source);
        copy.forEach(item -> Objects.requireNonNull(item, "contribution"));
        copy.sort(Comparator.comparingInt(ProviderContribution::precedence).reversed()
                .thenComparing(item -> item.providerId().value()));
        Set<ProviderId> seen = new HashSet<>();
        for (ProviderContribution contribution : copy) {
            if (!seen.add(contribution.providerId())) {
                throw new IllegalArgumentException("duplicate provider contribution: " + contribution.providerId());
            }
        }
        return List.copyOf(copy);
    }

    private static List<ProviderCompositionConflict> canonicalConflicts(List<ProviderCompositionConflict> source) {
        Objects.requireNonNull(source, "conflicts");
        List<ProviderCompositionConflict> copy = new ArrayList<>(source);
        copy.forEach(item -> Objects.requireNonNull(item, "conflict"));
        copy.sort(Comparator.comparing((ProviderCompositionConflict item) -> item.entityKind().name())
                .thenComparing(ProviderCompositionConflict::logicalKey)
                .thenComparing(item -> item.resolution().name()));
        Set<String> seen = new HashSet<>();
        for (ProviderCompositionConflict conflict : copy) {
            String key = conflict.entityKind().name() + "\u0000" + conflict.logicalKey();
            if (!seen.add(key)) {
                throw new IllegalArgumentException("duplicate provider conflict: " + key);
            }
        }
        return List.copyOf(copy);
    }
}
