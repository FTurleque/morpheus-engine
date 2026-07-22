package com.morpheus.domain.change;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of a normalized change proposal. */
public record ChangeId(DomainIdentity value) implements Comparable<ChangeId> {
    public ChangeId {
        Objects.requireNonNull(value, "value");
    }

    public static ChangeId generate() {
        return new ChangeId(DomainIdentity.generate());
    }

    public static ChangeId parse(String value) {
        return new ChangeId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(ChangeId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
