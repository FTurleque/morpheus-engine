package com.morpheus.application.store;

import com.morpheus.application.query.dsl.QueryScope;
import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewEntry;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewVersion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Technology-neutral persistence boundary for M24 saved query definitions and immutable revision history. */
public interface SavedViewStore {
    void create(SavedViewDefinition definition, SavedViewVersion version);

    Optional<SavedViewDefinition> find(SavedViewId id);

    /** Every row of the scope, in name order; a row whose definition cannot be decoded is reported, never skipped. */
    List<SavedViewEntry> list(QueryScope scope);

    List<SavedViewVersion> listVersions(SavedViewId id);

    long count(QueryScope scope);

    /**
     * Archives without decoding the stored definition, so a row that can no longer be read can still be retired.
     * Refuses an unknown id, an already archived view and a stale revision, in that order.
     */
    SavedViewEntry archive(SavedViewId id, long expectedRevision, Instant at);

    SavedViewDefinition compareAndSet(
            SavedViewId id,
            long expectedRevision,
            SavedViewDefinition replacement,
            SavedViewVersion version);
}
