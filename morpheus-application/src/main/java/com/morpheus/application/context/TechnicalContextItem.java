package com.morpheus.application.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral technical fragment selected by an external context engine. */
public record TechnicalContextItem(
        String type,
        String path,
        String symbol,
        Integer startLine,
        Integer endLine,
        String content,
        double score,
        Map<String, Double> scoreComponents,
        List<String> reasons,
        int estimatedTokens,
        boolean truncated) {

    public TechnicalContextItem {
        type = requireText(type, "type");
        path = requireText(path, "path");
        content = Objects.requireNonNullElse(content, "");
        scoreComponents = Map.copyOf(Objects.requireNonNull(scoreComponents, "scoreComponents"));
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (estimatedTokens < 0) {
            throw new IllegalArgumentException("estimatedTokens must be >= 0");
        }
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
