package com.morpheus.application.query.saved;

import java.time.Instant;
import java.util.Objects;

/**
 * One saved view as a listing can honestly report it: either fully readable, or present but with a stored
 * definition that could not be decoded.
 *
 * <p>A listing that decodes every row fails as a whole when one row is unreadable, which makes a single bad
 * row a denial of service on its scope; a listing that skips the row hides that it exists. The unreadable entry
 * carries everything that does not need the definition (identity, name, revision, status, timestamps) and the
 * reason the decoding failed, so the caller sees what could not be observed and can archive it.</p>
 */
public sealed interface SavedViewEntry permits SavedViewEntry.Readable, SavedViewEntry.Unreadable {
    SavedViewId id();

    String name();

    long revision();

    SavedViewStatus status();

    Instant createdAt();

    Instant updatedAt();

    record Readable(SavedViewDefinition definition) implements SavedViewEntry {
        public Readable {
            Objects.requireNonNull(definition, "definition");
        }

        @Override
        public SavedViewId id() {
            return definition.id();
        }

        @Override
        public String name() {
            return definition.name();
        }

        @Override
        public long revision() {
            return definition.revision();
        }

        @Override
        public SavedViewStatus status() {
            return definition.status();
        }

        @Override
        public Instant createdAt() {
            return definition.createdAt();
        }

        @Override
        public Instant updatedAt() {
            return definition.updatedAt();
        }
    }

    record Unreadable(
            SavedViewId id,
            String name,
            long revision,
            SavedViewStatus status,
            Instant createdAt,
            Instant updatedAt,
            String reason) implements SavedViewEntry {
        public Unreadable {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
