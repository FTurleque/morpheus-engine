package com.morpheus.application.traceability;

import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.Objects;

/** Canonical description of one explicit fact that can materialize one traceability-link observation. */
public record TraceabilityDerivationKey(
        TraceabilityEntityRef fact,
        TraceabilityEntityRef source,
        TraceabilityRelationType relationType,
        TraceabilityEntityRef target) implements Comparable<TraceabilityDerivationKey> {

    public TraceabilityDerivationKey {
        Objects.requireNonNull(fact, "fact");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public int compareTo(TraceabilityDerivationKey other) {
        int sourceComparison = source.compareTo(other.source);
        if (sourceComparison != 0) {
            return sourceComparison;
        }
        int relationComparison = relationType.compareTo(other.relationType);
        if (relationComparison != 0) {
            return relationComparison;
        }
        int targetComparison = target.compareTo(other.target);
        if (targetComparison != 0) {
            return targetComparison;
        }
        return fact.compareTo(other.fact);
    }
}
