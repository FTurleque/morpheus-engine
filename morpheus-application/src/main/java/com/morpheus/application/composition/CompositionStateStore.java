package com.morpheus.application.composition;

import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.util.Optional;

/** Persistence port for snapshot-scoped provider provenance and composition conflicts. */
public interface CompositionStateStore {
    void save(CompositionSnapshotState state);

    Optional<CompositionSnapshotState> find(KnowledgeSnapshotId snapshotId);
}
