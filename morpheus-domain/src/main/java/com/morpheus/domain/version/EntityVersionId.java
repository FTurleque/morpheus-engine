package com.morpheus.domain.version;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of one versioned occurrence of a logical domain entity. */
public record EntityVersionId(DomainIdentity value) implements Comparable<EntityVersionId> {
    public EntityVersionId {
        Objects.requireNonNull(value, "value");
    }

    public static EntityVersionId generate() {
        return new EntityVersionId(DomainIdentity.generate());
    }

    public static EntityVersionId parse(String value) {
        return new EntityVersionId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(EntityVersionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
