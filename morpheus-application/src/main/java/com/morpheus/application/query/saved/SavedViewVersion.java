package com.morpheus.application.query.saved;

import com.morpheus.application.query.dsl.QueryDefinition;

import java.time.Instant;
import java.util.Objects;

/** Immutable history entry for one saved-view revision. */
public record SavedViewVersion(
        SavedViewId id,
        long revision,
        String name,
        QueryDefinition query,
        SavedViewStatus status,
        Instant recordedAt) implements Comparable<SavedViewVersion> {

    public SavedViewVersion {
        Objects.requireNonNull(id, "id");
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be greater than zero");
        }
        Objects.requireNonNull(name, "name");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }

    @Override
    public int compareTo(SavedViewVersion other) {
        int byId = id.compareTo(other.id);
        return byId != 0 ? byId : Long.compare(revision, other.revision);
    }
}
