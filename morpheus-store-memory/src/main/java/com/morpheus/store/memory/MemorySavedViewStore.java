package com.morpheus.store.memory;

import com.morpheus.application.query.dsl.QueryScope;
import com.morpheus.application.query.saved.SavedViewConflictException;
import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewVersion;
import com.morpheus.application.store.SavedViewStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Thread-safe deterministic in-memory M24 saved-view adapter with immutable revision history. */
public final class MemorySavedViewStore implements SavedViewStore {
    private final Map<SavedViewId, SavedViewDefinition> definitions = new TreeMap<>();
    private final Map<SavedViewId, List<SavedViewVersion>> history = new TreeMap<>();

    @Override
    public synchronized void create(SavedViewDefinition definition, SavedViewVersion version) {
        if (!definition.id().equals(version.id()) || definition.revision() != version.revision()) {
            throw new IllegalArgumentException("saved view definition/version identity or revision mismatch");
        }
        if (definitions.containsKey(definition.id())) {
            throw new SavedViewConflictException("saved view already exists: " + definition.id());
        }
        definitions.put(definition.id(), definition);
        history.put(definition.id(), new ArrayList<>(List.of(version)));
    }

    @Override
    public synchronized Optional<SavedViewDefinition> find(SavedViewId id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public synchronized List<SavedViewDefinition> list(QueryScope scope) {
        return definitions.values().stream()
                .filter(item -> item.query().scope().equals(scope))
                .sorted()
                .toList();
    }

    @Override
    public synchronized List<SavedViewVersion> listVersions(SavedViewId id) {
        return List.copyOf(history.getOrDefault(id, List.of()));
    }

    @Override
    public synchronized long count(QueryScope scope) {
        return definitions.values().stream().filter(item -> item.query().scope().equals(scope)).count();
    }

    @Override
    public synchronized SavedViewDefinition compareAndSet(
            SavedViewId id,
            long expectedRevision,
            SavedViewDefinition replacement,
            SavedViewVersion version) {
        SavedViewDefinition current = definitions.get(id);
        if (current == null) {
            throw new IllegalArgumentException("unknown saved view: " + id);
        }
        if (current.revision() != expectedRevision) {
            throw new SavedViewConflictException(
                    "stale saved view revision: expected " + expectedRevision + " but current is " + current.revision());
        }
        if (!replacement.id().equals(id) || !version.id().equals(id)) {
            throw new IllegalArgumentException("saved view replacement identity mismatch");
        }
        long nextRevision = expectedRevision + 1;
        if (replacement.revision() != nextRevision || version.revision() != nextRevision) {
            throw new IllegalArgumentException("saved view replacement must advance revision by exactly one");
        }
        definitions.put(id, replacement);
        history.computeIfAbsent(id, ignored -> new ArrayList<>()).add(version);
        return replacement;
    }
}
