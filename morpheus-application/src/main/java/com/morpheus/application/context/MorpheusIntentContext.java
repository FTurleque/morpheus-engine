package com.morpheus.application.context;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** MORPHEUS-owned intent seed kept separate from external technical context. */
public record MorpheusIntentContext(
        String subjectType,
        String subjectId,
        Optional<String> key,
        String title,
        String intent,
        List<String> scope,
        List<String> affectedRequirements,
        List<String> constraints,
        List<String> designDecisions,
        List<String> implementationTasks,
        String query) {

    public MorpheusIntentContext {
        subjectType = requireText(subjectType, "subjectType");
        subjectId = requireText(subjectId, "subjectId");
        key = Objects.requireNonNull(key, "key").map(String::trim).filter(value -> !value.isEmpty());
        title = requireText(title, "title");
        intent = requireText(intent, "intent");
        scope = List.copyOf(Objects.requireNonNull(scope, "scope"));
        affectedRequirements = List.copyOf(Objects.requireNonNull(affectedRequirements, "affectedRequirements"));
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        designDecisions = List.copyOf(Objects.requireNonNull(designDecisions, "designDecisions"));
        implementationTasks = List.copyOf(Objects.requireNonNull(implementationTasks, "implementationTasks"));
        query = requireText(query, "query");
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
