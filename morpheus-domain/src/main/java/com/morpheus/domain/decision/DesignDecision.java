package com.morpheus.domain.decision;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.provenance.Provenance;

import java.util.Objects;

/** Provider-neutral design decision attached to a normalized change proposal. */
public record DesignDecision(
        DesignDecisionId id,
        ChangeId changeId,
        String title,
        String decision,
        Provenance provenance) {

    public DesignDecision {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(changeId, "changeId");
        title = requireNonBlank(title, "title");
        decision = requireNonBlank(decision, "decision");
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
