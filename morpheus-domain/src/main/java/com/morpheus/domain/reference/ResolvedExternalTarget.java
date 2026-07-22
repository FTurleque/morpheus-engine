package com.morpheus.domain.reference;

import java.util.Map;
import java.util.Objects;

/** Canonical target returned by an optional external resolver. */
public record ResolvedExternalTarget(
        ExternalReferenceTarget target,
        Map<String, String> attributes) {

    public ResolvedExternalTarget {
        Objects.requireNonNull(target, "target");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        if (attributes.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getKey().isBlank()
                || entry.getValue() == null)) {
            throw new IllegalArgumentException("attributes must contain non-blank keys and non-null values");
        }
    }
}
