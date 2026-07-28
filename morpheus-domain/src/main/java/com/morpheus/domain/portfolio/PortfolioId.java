package com.morpheus.domain.portfolio;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** Stable MORPHEUS identity for a portfolio; never derived from a path or repository URL. */
public record PortfolioId(DomainIdentity value) implements Comparable<PortfolioId> {
    public PortfolioId {
        Objects.requireNonNull(value, "value");
    }

    public static PortfolioId generate() {
        return new PortfolioId(DomainIdentity.generate());
    }

    public static PortfolioId parse(String value) {
        return new PortfolioId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(PortfolioId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
