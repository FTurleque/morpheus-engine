package com.morpheus.domain.requirement;

import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.specification.SpecificationId;

import java.util.Objects;
import java.util.Optional;

/** Provider-neutral normalized requirement. */
public record Requirement(
        RequirementId id,
        SpecificationId specificationId,
        Optional<String> key,
        String title,
        String statement,
        Provenance provenance) {

    public Requirement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(specificationId, "specificationId");
        key = Objects.requireNonNull(key, "key")
                .map(String::trim)
                .filter(value -> !value.isEmpty());
        title = requireNonBlank(title, "title");
        statement = requireNonBlank(statement, "statement");
        Objects.requireNonNull(provenance, "provenance");
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
