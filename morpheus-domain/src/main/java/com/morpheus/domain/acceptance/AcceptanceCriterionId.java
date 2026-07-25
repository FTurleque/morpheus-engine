package com.morpheus.domain.acceptance;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of a normalized acceptance criterion. */
public record AcceptanceCriterionId(DomainIdentity value) implements Comparable<AcceptanceCriterionId> {
    public AcceptanceCriterionId {
        Objects.requireNonNull(value, "value");
    }

    public static AcceptanceCriterionId generate() {
        return new AcceptanceCriterionId(DomainIdentity.generate());
    }

    public static AcceptanceCriterionId parse(String value) {
        return new AcceptanceCriterionId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(AcceptanceCriterionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
