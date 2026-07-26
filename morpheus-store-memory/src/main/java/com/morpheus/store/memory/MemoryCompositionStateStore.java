package com.morpheus.store.memory;

import com.morpheus.application.composition.CompositionSnapshotState;
import com.morpheus.application.composition.CompositionStateStore;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory M18 composition metadata store. */
public final class MemoryCompositionStateStore implements CompositionStateStore {
    private final Map<KnowledgeSnapshotId, CompositionSnapshotState> states = new LinkedHashMap<>();

    @Override
    public synchronized void save(CompositionSnapshotState state) {
        Objects.requireNonNull(state, "state");
        states.put(state.snapshotId(), state);
    }

    @Override
    public synchronized Optional<CompositionSnapshotState> find(KnowledgeSnapshotId snapshotId) {
        return Optional.ofNullable(states.get(Objects.requireNonNull(snapshotId, "snapshotId")));
    }
}
