package com.morpheus.domain.specification;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;

import java.util.Objects;
import java.util.Optional;

/** Provider-neutral normalized specification content. */
public record Specification(
        SpecificationId id,
        ProjectSpecificationId projectId,
        String key,
        String title,
        Optional<String> description,
        Provenance provenance) {

    public Specification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        key = requireNonBlank(key, "key");
        title = requireNonBlank(title, "title");
        description = Objects.requireNonNull(description, "description")
                .map(String::trim)
                .filter(value -> !value.isEmpty());
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
