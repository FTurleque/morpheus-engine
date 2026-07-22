package com.morpheus.domain.version;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of one logical specification version. */
public record SpecificationVersionId(DomainIdentity value) implements Comparable<SpecificationVersionId> {
    public SpecificationVersionId {
        Objects.requireNonNull(value, "value");
    }

    public static SpecificationVersionId generate() {
        return new SpecificationVersionId(DomainIdentity.generate());
    }

    public static SpecificationVersionId parse(String value) {
        return new SpecificationVersionId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(SpecificationVersionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
