package com.morpheus.application.snapshot;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SnapshotConflictException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;

import java.util.Objects;
import java.util.Optional;

/** Coordinates the technical snapshot lifecycle without owning backend-specific atomicity. */
public final class SnapshotLifecycleService {
    private final SpecificationKnowledgeStore store;

    public SnapshotLifecycleService(SpecificationKnowledgeStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public KnowledgeSnapshotMetadata registerBuilding(KnowledgeSnapshotMetadata snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.state() != KnowledgeSnapshotState.BUILDING) {
            throw new SnapshotConflictException("new snapshot candidates must start in BUILDING");
        }
        store.putSnapshot(snapshot);
        return store.findSnapshot(snapshot.id()).orElseThrow();
    }

    public KnowledgeSnapshotMetadata validate(KnowledgeSnapshotId snapshotId, SnapshotValidator validator) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(validator, "validator");

        KnowledgeSnapshotMetadata validating = store.transitionSnapshotState(
                snapshotId,
                KnowledgeSnapshotState.BUILDING,
                KnowledgeSnapshotState.VALIDATING);

        try {
            SnapshotValidationResult result = Objects.requireNonNull(
                    validator.validate(validating),
                    "validator result");
            KnowledgeSnapshotState target = result.isValid()
                    ? KnowledgeSnapshotState.READY
                    : KnowledgeSnapshotState.FAILED;
            return store.transitionSnapshotState(
                    snapshotId,
                    KnowledgeSnapshotState.VALIDATING,
                    target);
        } catch (RuntimeException exception) {
            markValidationFailure(snapshotId, exception);
            throw new SnapshotValidationException("Snapshot validation failed unexpectedly: " + snapshotId, exception);
        }
    }

    public KnowledgeSnapshotMetadata activate(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata target = store.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("snapshot not found: " + snapshotId));
        return store.activateSnapshot(snapshotId, target.predecessorId());
    }

    public Optional<KnowledgeSnapshotMetadata> current(ProjectSpecificationId projectId) {
        return store.activeSnapshot(Objects.requireNonNull(projectId, "projectId"));
    }

    private void markValidationFailure(KnowledgeSnapshotId snapshotId, RuntimeException originalFailure) {
        try {
            store.transitionSnapshotState(
                    snapshotId,
                    KnowledgeSnapshotState.VALIDATING,
                    KnowledgeSnapshotState.FAILED);
        } catch (RuntimeException stateFailure) {
            originalFailure.addSuppressed(stateFailure);
        }
    }
}
