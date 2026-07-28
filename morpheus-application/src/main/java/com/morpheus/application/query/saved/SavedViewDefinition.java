package com.morpheus.application.query.saved;

import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;

import java.time.Instant;
import java.util.Objects;

/** Current persisted definition of one saved query view; results are never materialized here. */
public record SavedViewDefinition(
        SavedViewId id,
        String name,
        QueryDefinition query,
        long revision,
        SavedViewStatus status,
        Instant createdAt,
        Instant updatedAt) implements Comparable<SavedViewDefinition> {

    public SavedViewDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("saved view name must not be blank");
        }
        if (name.length() > QueryBudgets.MAX_SAVED_VIEW_NAME) {
            throw new IllegalArgumentException("saved view name exceeds " + QueryBudgets.MAX_SAVED_VIEW_NAME + " characters");
        }
        Objects.requireNonNull(query, "query");
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be greater than zero");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }

    @Override
    public int compareTo(SavedViewDefinition other) {
        int byName = name.compareToIgnoreCase(other.name);
        return byName != 0 ? byName : id.compareTo(other.id);
    }
}
