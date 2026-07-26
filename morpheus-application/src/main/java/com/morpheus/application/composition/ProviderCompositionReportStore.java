package com.morpheus.application.composition;

import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.util.Optional;

/** Snapshot-scoped persistence port for provider composition explanations. */
public interface ProviderCompositionReportStore {
    void put(KnowledgeSnapshotId snapshotId, ProviderCompositionReport report);

    Optional<ProviderCompositionReport> find(KnowledgeSnapshotId snapshotId);
}
