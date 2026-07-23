package com.morpheus.application.query;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.Objects;
import java.util.Optional;

/** Snapshot-aware result for a deterministic single-item business query. */
public record SnapshotItemResult<T>(
        KnowledgeSnapshotMetadata snapshot,
        Optional<T> item) {

    public SnapshotItemResult {
        Objects.requireNonNull(snapshot, "snapshot");
        item = Objects.requireNonNull(item, "item");
    }
}
