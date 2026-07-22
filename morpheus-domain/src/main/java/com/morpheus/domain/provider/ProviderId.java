package com.morpheus.domain.provider;

import java.util.Objects;

/** Stable adapter identifier, distinct from specification domain identity. */
public record ProviderId(String value) implements Comparable<ProviderId> {

    public ProviderId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("provider id must not be blank");
        }
    }

    @Override
    public int compareTo(ProviderId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
