package com.morpheus.domain.portfolio;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

public record CrossProjectReferenceId(DomainIdentity value) implements Comparable<CrossProjectReferenceId> {
    public CrossProjectReferenceId {
        Objects.requireNonNull(value, "value");
    }

    public static CrossProjectReferenceId generate() {
        return new CrossProjectReferenceId(DomainIdentity.generate());
    }

    public static CrossProjectReferenceId parse(String value) {
        return new CrossProjectReferenceId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(CrossProjectReferenceId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
