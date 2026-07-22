package com.morpheus.domain.constraint;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of a normalized constraint. */
public record ConstraintId(DomainIdentity value) implements Comparable<ConstraintId> {
    public ConstraintId {
        Objects.requireNonNull(value, "value");
    }

    public static ConstraintId generate() {
        return new ConstraintId(DomainIdentity.generate());
    }

    public static ConstraintId parse(String value) {
        return new ConstraintId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(ConstraintId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
