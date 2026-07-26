package com.morpheus.application.composition;

import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.util.Objects;
import java.util.Optional;

/** Read-only M18 query service for persisted provider-composition reports. */
public final class ProviderCompositionQueryService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final ProviderCompositionReportStore reportStore;

    public ProviderCompositionQueryService(
            SpecificationKnowledgeStore snapshotStore,
            ProviderCompositionReportStore reportStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.reportStore = Objects.requireNonNull(reportStore, "reportStore");
    }

    public Optional<ProviderCompositionView> active(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return snapshotStore.activeSnapshot(projectId)
                .flatMap(snapshot -> reportStore.find(snapshot.id())
                        .map(report -> ProviderCompositionView.from(snapshot.id(), report)));
    }

    public Optional<ProviderCompositionView> snapshot(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        if (snapshotStore.findSnapshot(snapshotId).isEmpty()) {
            return Optional.empty();
        }
        return reportStore.find(snapshotId)
                .map(report -> ProviderCompositionView.from(snapshotId, report));
    }
}
