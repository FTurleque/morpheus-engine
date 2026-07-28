package com.morpheus.domain.portfolio;

import java.time.Instant;
import java.util.Objects;

public record PortfolioDefinition(
        PortfolioId id,
        String name,
        Instant createdAt,
        Instant updatedAt) implements Comparable<PortfolioDefinition> {

    public PortfolioDefinition {
        Objects.requireNonNull(id, "id");
        name = requireText(name, "name", 256);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public PortfolioDefinition rename(String newName, Instant at) {
        Objects.requireNonNull(at, "at");
        if (at.isBefore(updatedAt)) {
            throw new IllegalArgumentException("updatedAt must not move backwards");
        }
        return new PortfolioDefinition(id, newName, createdAt, at);
    }

    @Override
    public int compareTo(PortfolioDefinition other) {
        return id.compareTo(other.id);
    }

    private static String requireText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must contain 1.." + maximumLength + " characters");
        }
        return trimmed;
    }
}
