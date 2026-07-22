package com.morpheus.domain.decision;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of a normalized design decision. */
public record DesignDecisionId(DomainIdentity value) implements Comparable<DesignDecisionId> {
    public DesignDecisionId {
        Objects.requireNonNull(value, "value");
    }

    public static DesignDecisionId generate() {
        return new DesignDecisionId(DomainIdentity.generate());
    }

    public static DesignDecisionId parse(String value) {
        return new DesignDecisionId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(DesignDecisionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
