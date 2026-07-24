package com.morpheus.application.context;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.Objects;
import java.util.Optional;

/** Canonical JSON-safe snapshot projection for live augmented-context responses. */
public record AugmentedSnapshotView(
        String id,
        String projectId,
        Optional<String> predecessorId,
        String state,
        Optional<String> sourceRevision,
        String createdAt) {

    public AugmentedSnapshotView {
        id = requireText(id, "id");
        projectId = requireText(projectId, "projectId");
        predecessorId = Objects.requireNonNull(predecessorId, "predecessorId");
        state = requireText(state, "state");
        sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision");
        createdAt = requireText(createdAt, "createdAt");
    }

    public static AugmentedSnapshotView from(KnowledgeSnapshotMetadata snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new AugmentedSnapshotView(
                snapshot.id().toString(),
                snapshot.projectId().toString(),
                snapshot.predecessorId().map(Object::toString),
                snapshot.state().name(),
                snapshot.sourceRevision(),
                snapshot.createdAt().toString());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
