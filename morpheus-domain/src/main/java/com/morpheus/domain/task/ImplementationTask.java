package com.morpheus.domain.task;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.provenance.Provenance;

import java.util.Objects;
import java.util.Optional;

/** Provider-neutral implementation task without the M3 change lifecycle state machine. */
public record ImplementationTask(
        TaskId id,
        ChangeId changeId,
        Optional<String> key,
        String title,
        boolean completed,
        Provenance provenance) {

    public ImplementationTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(changeId, "changeId");
        key = Objects.requireNonNull(key, "key").map(String::trim).filter(candidate -> !candidate.isEmpty());
        title = requireNonBlank(title, "title");
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
