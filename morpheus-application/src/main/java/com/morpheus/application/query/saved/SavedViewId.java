package com.morpheus.application.query.saved;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** Stable saved-view identity; deliberately independent from its mutable display name. */
public record SavedViewId(DomainIdentity value) implements Comparable<SavedViewId> {
    public SavedViewId {
        Objects.requireNonNull(value, "value");
    }

    public static SavedViewId generate() {
        return new SavedViewId(DomainIdentity.generate());
    }

    public static SavedViewId parse(String value) {
        return new SavedViewId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(SavedViewId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
