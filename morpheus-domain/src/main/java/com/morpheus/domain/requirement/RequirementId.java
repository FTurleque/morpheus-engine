package com.morpheus.domain.requirement;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of a normalized requirement. */
public record RequirementId(DomainIdentity value) implements Comparable<RequirementId> {
    public RequirementId {
        Objects.requireNonNull(value, "value");
    }

    public static RequirementId generate() {
        return new RequirementId(DomainIdentity.generate());
    }

    public static RequirementId parse(String value) {
        return new RequirementId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(RequirementId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
