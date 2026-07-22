package com.morpheus.application.temporal;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Provider-neutral temporal projection over versioned domain occurrences.
 *
 * <p>The CURRENT view is deliberately derived only from explicit {@link TemporalState#CURRENT}
 * occurrences. Proposed or historical content is never promoted implicitly.</p>
 */
public final class TemporalProjection<T> {
    private final List<EntityVersion<T>> occurrences;

    public TemporalProjection(Collection<EntityVersion<T>> occurrences) {
        Objects.requireNonNull(occurrences, "occurrences");
        this.occurrences = List.copyOf(occurrences);
        validate();
    }

    public List<EntityVersion<T>> all() {
        return occurrences;
    }

    public List<EntityVersion<T>> current() {
        return byState(TemporalState.CURRENT);
    }

    public List<EntityVersion<T>> proposed() {
        return byState(TemporalState.PROPOSED);
    }

    public List<EntityVersion<T>> historical() {
        return byState(TemporalState.HISTORICAL);
    }

    public List<EntityVersion<T>> byState(TemporalState state) {
        Objects.requireNonNull(state, "state");
        return occurrences.stream()
                .filter(occurrence -> occurrence.temporalState() == state)
                .toList();
    }

    public List<EntityVersion<T>> forEntity(DomainIdentity entityIdentity) {
        Objects.requireNonNull(entityIdentity, "entityIdentity");
        return occurrences.stream()
                .filter(occurrence -> occurrence.entityIdentity().equals(entityIdentity))
                .toList();
    }

    public Optional<EntityVersion<T>> currentFor(DomainIdentity entityIdentity) {
        Objects.requireNonNull(entityIdentity, "entityIdentity");
        return occurrences.stream()
                .filter(occurrence -> occurrence.entityIdentity().equals(entityIdentity))
                .filter(occurrence -> occurrence.temporalState() == TemporalState.CURRENT)
                .findFirst();
    }

    private void validate() {
        Set<EntityVersionId> versionIds = new HashSet<>();
        Map<DomainIdentity, Integer> currentCountByIdentity = new HashMap<>();

        for (EntityVersion<T> occurrence : occurrences) {
            if (!versionIds.add(occurrence.id())) {
                throw new IllegalArgumentException("duplicate entity version identity: " + occurrence.id());
            }

            if (occurrence.temporalState() == TemporalState.CURRENT) {
                int currentCount = currentCountByIdentity.merge(occurrence.entityIdentity(), 1, Integer::sum);
                if (currentCount > 1) {
                    throw new IllegalArgumentException(
                            "multiple CURRENT occurrences for logical identity: " + occurrence.entityIdentity());
                }
            }
        }
    }
}
