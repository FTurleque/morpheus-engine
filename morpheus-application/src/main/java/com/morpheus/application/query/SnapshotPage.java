package com.morpheus.application.query;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.List;
import java.util.Objects;

/** Snapshot-aware deterministic page for bounded business-content lists. */
public record SnapshotPage<T>(
        KnowledgeSnapshotMetadata snapshot,
        List<T> items,
        PageRequest pageRequest,
        int totalMatches,
        boolean hasMore) {

    public SnapshotPage {
        Objects.requireNonNull(snapshot, "snapshot");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(pageRequest, "pageRequest");
        if (totalMatches < 0) {
            throw new IllegalArgumentException("totalMatches must be >= 0");
        }
        if (items.size() > pageRequest.limit()) {
            throw new IllegalArgumentException("items must not exceed page limit");
        }
        if (hasMore && pageRequest.offset() + items.size() >= totalMatches) {
            throw new IllegalArgumentException("hasMore is inconsistent with page bounds");
        }
        if (!hasMore && pageRequest.offset() + items.size() < totalMatches) {
            throw new IllegalArgumentException("hasMore is inconsistent with page bounds");
        }
    }
}
