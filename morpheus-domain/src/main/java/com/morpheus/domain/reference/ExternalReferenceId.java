package com.morpheus.domain.reference;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of an external reference. */
public record ExternalReferenceId(DomainIdentity value) implements Comparable<ExternalReferenceId> {
    public ExternalReferenceId {
        Objects.requireNonNull(value, "value");
    }

    public static ExternalReferenceId generate() {
        return new ExternalReferenceId(DomainIdentity.generate());
    }

    public static ExternalReferenceId parse(String value) {
        return new ExternalReferenceId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(ExternalReferenceId other) {
        return value.compareTo(other.value());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
