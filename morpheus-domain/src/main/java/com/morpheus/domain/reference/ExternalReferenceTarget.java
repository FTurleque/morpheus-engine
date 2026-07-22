package com.morpheus.domain.reference;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Provider-neutral coordinates of a resource owned by another system. */
public record ExternalReferenceTarget(
        String system,
        Optional<String> project,
        String resourceType,
        String externalId,
        Optional<String> revision) {

    public ExternalReferenceTarget {
        system = normalizeToken(system, "system");
        project = normalizeOptional(project, "project");
        resourceType = normalizeToken(resourceType, "resourceType");
        externalId = requireNonBlank(externalId, "externalId");
        revision = normalizeOptional(revision, "revision");
    }

    private static String normalizeToken(String value, String name) {
        return requireNonBlank(value, name).toUpperCase(Locale.ROOT);
    }

    private static Optional<String> normalizeOptional(Optional<String> value, String name) {
        return Objects.requireNonNull(value, name)
                .map(String::trim)
                .filter(candidate -> !candidate.isEmpty());
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
