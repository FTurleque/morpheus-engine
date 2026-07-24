package com.morpheus.application.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral technical context already selected and budgeted by the external engine. */
public record TechnicalContextBundle(
        String projectId,
        String projectName,
        String query,
        boolean explain,
        long durationMs,
        int tokenBudget,
        int estimatedTokens,
        List<TechnicalContextItem> items,
        List<Map<String, Object>> excluded,
        Map<String, Object> metadata) {

    public TechnicalContextBundle {
        projectId = requireText(projectId, "projectId");
        projectName = requireText(projectName, "projectName");
        query = requireText(query, "query");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be >= 0");
        }
        if (tokenBudget < 1) {
            throw new IllegalArgumentException("tokenBudget must be > 0");
        }
        if (estimatedTokens < 0 || estimatedTokens > tokenBudget) {
            throw new IllegalArgumentException("estimatedTokens must be between 0 and tokenBudget");
        }
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        excluded = Objects.requireNonNull(excluded, "excluded").stream().map(Map::copyOf).toList();
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
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
