package com.morpheus.domain.requirement;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of one normalized requirement-delta occurrence. */
public record RequirementDeltaId(DomainIdentity value) implements Comparable<RequirementDeltaId> {
    public RequirementDeltaId {
        Objects.requireNonNull(value, "value");
    }

    public static RequirementDeltaId generate() {
        return new RequirementDeltaId(DomainIdentity.generate());
    }

    public static RequirementDeltaId parse(String value) {
        return new RequirementDeltaId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(RequirementDeltaId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
