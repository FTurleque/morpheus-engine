package com.morpheus.domain.traceability;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of one traceability-link observation. */
public record TraceabilityLinkId(DomainIdentity value) implements Comparable<TraceabilityLinkId> {
    public TraceabilityLinkId {
        Objects.requireNonNull(value, "value");
    }

    public static TraceabilityLinkId generate() {
        return new TraceabilityLinkId(DomainIdentity.generate());
    }

    public static TraceabilityLinkId parse(String value) {
        return new TraceabilityLinkId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(TraceabilityLinkId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
