package com.morpheus.domain.snapshot;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of a knowledge snapshot. */
public record KnowledgeSnapshotId(DomainIdentity value) implements Comparable<KnowledgeSnapshotId> {
    public KnowledgeSnapshotId {
        Objects.requireNonNull(value, "value");
    }

    public static KnowledgeSnapshotId generate() {
        return new KnowledgeSnapshotId(DomainIdentity.generate());
    }

    public static KnowledgeSnapshotId parse(String value) {
        return new KnowledgeSnapshotId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(KnowledgeSnapshotId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
