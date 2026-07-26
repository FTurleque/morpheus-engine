package com.morpheus.domain.change.lifecycle;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** Stable MORPHEUS identity for one controlled lifecycle mutation. */
public record ChangeLifecycleMutationId(DomainIdentity value) implements Comparable<ChangeLifecycleMutationId> {
    public ChangeLifecycleMutationId {
        Objects.requireNonNull(value, "value");
    }

    public static ChangeLifecycleMutationId generate() {
        return new ChangeLifecycleMutationId(DomainIdentity.generate());
    }

    public static ChangeLifecycleMutationId parse(String value) {
        return new ChangeLifecycleMutationId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(ChangeLifecycleMutationId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
