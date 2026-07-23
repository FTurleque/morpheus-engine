package com.morpheus.application.history;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.specification.SpecificationId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit delta plan that reconstructs one historical Requirement projection through the S5 pipeline. */
public record RequirementLogicalRollbackPlan(
        KnowledgeSnapshotMetadata currentSnapshot,
        KnowledgeSnapshotMetadata targetHistoricalSnapshot,
        ChangeId changeId,
        List<RequirementDelta> deltas,
        Map<String, SpecificationId> specificationIdsByKey) {

    public RequirementLogicalRollbackPlan {
        Objects.requireNonNull(currentSnapshot, "currentSnapshot");
        Objects.requireNonNull(targetHistoricalSnapshot, "targetHistoricalSnapshot");
        Objects.requireNonNull(changeId, "changeId");
        deltas = List.copyOf(Objects.requireNonNull(deltas, "deltas"));
        specificationIdsByKey = Map.copyOf(Objects.requireNonNull(specificationIdsByKey, "specificationIdsByKey"));
        if (!currentSnapshot.projectId().equals(targetHistoricalSnapshot.projectId())) {
            throw new IllegalArgumentException("rollback snapshots must belong to the same project");
        }
    }

    public boolean isNoOp() {
        return deltas.isEmpty();
    }
}