package com.morpheus.domain.specification;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of a normalized specification. */
public record SpecificationId(DomainIdentity value) implements Comparable<SpecificationId> {
    public SpecificationId {
        Objects.requireNonNull(value, "value");
    }

    public static SpecificationId generate() {
        return new SpecificationId(DomainIdentity.generate());
    }

    public static SpecificationId parse(String value) {
        return new SpecificationId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(SpecificationId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
