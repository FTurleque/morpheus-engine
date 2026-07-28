package com.morpheus.application.store;

import com.morpheus.application.query.dsl.QueryScope;
import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewVersion;

import java.util.List;
import java.util.Optional;

/** Technology-neutral persistence boundary for M24 saved query definitions and immutable revision history. */
public interface SavedViewStore {
    void create(SavedViewDefinition definition, SavedViewVersion version);

    Optional<SavedViewDefinition> find(SavedViewId id);

    List<SavedViewDefinition> list(QueryScope scope);

    List<SavedViewVersion> listVersions(SavedViewId id);

    long count(QueryScope scope);

    SavedViewDefinition compareAndSet(
            SavedViewId id,
            long expectedRevision,
            SavedViewDefinition replacement,
            SavedViewVersion version);
}
