package com.morpheus.domain.scenario;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of a normalized scenario. */
public record ScenarioId(DomainIdentity value) implements Comparable<ScenarioId> {
    public ScenarioId {
        Objects.requireNonNull(value, "value");
    }

    public static ScenarioId generate() {
        return new ScenarioId(DomainIdentity.generate());
    }

    public static ScenarioId parse(String value) {
        return new ScenarioId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(ScenarioId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
