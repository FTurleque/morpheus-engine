package com.morpheus.domain.traceability;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** Typed reference to one MORPHEUS-owned entity identity participating in traceability. */
public record TraceabilityEntityRef(
        TraceabilityEntityKind kind,
        DomainIdentity identity) implements Comparable<TraceabilityEntityRef> {

    public TraceabilityEntityRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(identity, "identity");
    }

    @Override
    public int compareTo(TraceabilityEntityRef other) {
        int kindComparison = kind.compareTo(other.kind);
        return kindComparison != 0 ? kindComparison : identity.compareTo(other.identity);
    }
}
