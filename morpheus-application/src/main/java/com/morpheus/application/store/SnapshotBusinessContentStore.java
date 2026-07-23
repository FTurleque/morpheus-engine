package com.morpheus.application.store;

import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.util.Optional;

/** Technology-neutral persistence port for snapshot-owned non-Requirement business content. */
public interface SnapshotBusinessContentStore {
    void putSnapshotContent(SnapshotBusinessContent content);

    Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId);
}
