package com.morpheus.application.context;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Caller-controlled pass-through options for an external technical context engine. */
public record TechnicalContextOptions(
        String externalProject,
        int tokenBudget,
        Set<String> requestedSources,
        Map<String, String> constraints,
        boolean explain) {

    public static final int DEFAULT_TOKEN_BUDGET = 2_000;
    public static final int MAX_TOKEN_BUDGET = 100_000;
    public static final Set<String> ALLOWED_SOURCES = Set.of(
            "FILE", "SYMBOL", "TEST", "DOCUMENTATION", "INSTRUCTION", "SKILL", "GIT");

    public TechnicalContextOptions {
        externalProject = requireText(externalProject, "externalProject");
        if (tokenBudget < 1 || tokenBudget > MAX_TOKEN_BUDGET) {
            throw new IllegalArgumentException("tokenBudget must be between 1 and " + MAX_TOKEN_BUDGET);
        }
        requestedSources = Set.copyOf(Objects.requireNonNull(requestedSources, "requestedSources"));
        if (!ALLOWED_SOURCES.containsAll(requestedSources)) {
            Set<String> unsupported = new java.util.TreeSet<>(requestedSources);
            unsupported.removeAll(ALLOWED_SOURCES);
            throw new IllegalArgumentException("unsupported technical context sources: " + unsupported);
        }
        constraints = Map.copyOf(Objects.requireNonNull(constraints, "constraints"));
        constraints.forEach((key, value) -> {
            requireText(key, "constraint key");
            requireText(value, "constraint value");
        });
    }

    public static TechnicalContextOptions defaults(String externalProject) {
        return new TechnicalContextOptions(externalProject, DEFAULT_TOKEN_BUDGET, Set.of(), Map.of(), false);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
