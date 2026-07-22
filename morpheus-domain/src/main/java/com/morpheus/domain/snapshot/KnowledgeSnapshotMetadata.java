package com.morpheus.domain.snapshot;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Minimal snapshot metadata used by the M1 storage foundation. */
public record KnowledgeSnapshotMetadata(
        KnowledgeSnapshotId id,
        ProjectSpecificationId projectId,
        Optional<KnowledgeSnapshotId> predecessorId,
        KnowledgeSnapshotState state,
        Optional<String> sourceRevision,
        Instant createdAt) {

    public KnowledgeSnapshotMetadata {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        predecessorId = Objects.requireNonNull(predecessorId, "predecessorId");
        Objects.requireNonNull(state, "state");
        sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision")
                .map(String::trim)
                .filter(value -> !value.isEmpty());
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public KnowledgeSnapshotMetadata withState(KnowledgeSnapshotState newState) {
        return new KnowledgeSnapshotMetadata(
                id,
                projectId,
                predecessorId,
                Objects.requireNonNull(newState, "newState"),
                sourceRevision,
                createdAt);
    }
}
