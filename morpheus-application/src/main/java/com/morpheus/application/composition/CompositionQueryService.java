package com.morpheus.application.composition;

import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.Objects;
import java.util.Optional;

/** Reads the persisted composition metadata attached to the ACTIVE snapshot. */
public final class CompositionQueryService {
    private final SpecificationKnowledgeStore snapshots;
    private final CompositionStateStore compositions;

    public CompositionQueryService(
            SpecificationKnowledgeStore snapshots,
            CompositionStateStore compositions) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.compositions = Objects.requireNonNull(compositions, "compositions");
    }

    public Optional<CompositionStateView> findActive(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return snapshots.activeSnapshot(projectId)
                .flatMap(snapshot -> compositions.find(snapshot.id()))
                .map(CompositionStateView::from);
    }
}
