package com.morpheus.store.memory;

import com.morpheus.application.composition.ProviderCompositionReport;
import com.morpheus.application.composition.ProviderCompositionReportStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reference in-memory adapter for immutable snapshot-scoped provider composition reports. */
public final class MemoryProviderCompositionReportStore implements ProviderCompositionReportStore {
    private final SpecificationKnowledgeStore snapshotStore;
    private final Map<KnowledgeSnapshotId, ProviderCompositionReport> reports = new HashMap<>();

    public MemoryProviderCompositionReportStore(SpecificationKnowledgeStore snapshotStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
    }

    @Override
    public synchronized void put(KnowledgeSnapshotId snapshotId, ProviderCompositionReport report) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(report, "report");
        if (snapshotStore.findSnapshot(snapshotId).isEmpty()) {
            throw new KnowledgeStoreException("snapshot not found: " + snapshotId);
        }
        ProviderCompositionReport existing = reports.get(snapshotId);
        if (existing != null) {
            if (!existing.equals(report)) {
                throw new KnowledgeStoreException("provider composition report collision: " + snapshotId);
            }
            return;
        }
        reports.put(snapshotId, report);
    }

    @Override
    public synchronized Optional<ProviderCompositionReport> find(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        return Optional.ofNullable(reports.get(snapshotId));
    }
}
