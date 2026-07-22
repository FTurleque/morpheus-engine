package com.morpheus.domain.constraint;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.provenance.Provenance;

import java.util.Objects;

/** Provider-neutral constraint attached to a normalized change proposal. */
public record Constraint(
        ConstraintId id,
        ChangeId changeId,
        String statement,
        Provenance provenance) {

    public Constraint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(changeId, "changeId");
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
