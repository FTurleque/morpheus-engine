package com.morpheus.domain.project;

import com.morpheus.domain.source.SourceLocator;

import java.util.Objects;

/** Provider-neutral MORPHEUS project knowledge scope. */
public record ProjectSpecification(
        ProjectSpecificationId id,
        String displayName,
        SourceLocator rootLocator) {

    public ProjectSpecification {
        Objects.requireNonNull(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(rootLocator, "rootLocator");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
