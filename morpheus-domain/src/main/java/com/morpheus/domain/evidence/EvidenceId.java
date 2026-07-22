package com.morpheus.domain.evidence;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of one evidence item. */
public record EvidenceId(DomainIdentity value) implements Comparable<EvidenceId> {
    public EvidenceId {
        Objects.requireNonNull(value, "value");
    }

    public static EvidenceId generate() {
        return new EvidenceId(DomainIdentity.generate());
    }

    public static EvidenceId parse(String value) {
        return new EvidenceId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(EvidenceId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
