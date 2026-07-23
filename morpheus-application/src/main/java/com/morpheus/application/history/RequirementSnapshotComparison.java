package com.morpheus.application.history;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.List;
import java.util.Objects;

/** Deterministic comparison result between two published requirement projections. */
public record RequirementSnapshotComparison(
        KnowledgeSnapshotMetadata sourceSnapshot,
        KnowledgeSnapshotMetadata targetSnapshot,
        List<RequirementSnapshotDifference> differences) {

    public RequirementSnapshotComparison {
        Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        Objects.requireNonNull(targetSnapshot, "targetSnapshot");
        differences = List.copyOf(Objects.requireNonNull(differences, "differences"));
        if (!sourceSnapshot.projectId().equals(targetSnapshot.projectId())) {
            throw new IllegalArgumentException("snapshot comparison cannot cross project boundaries");
        }
    }
}