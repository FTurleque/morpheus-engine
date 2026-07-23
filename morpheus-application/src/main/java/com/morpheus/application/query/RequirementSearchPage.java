package com.morpheus.application.query;

import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.List;
import java.util.Objects;

/** Immutable result page for a snapshot-coherent requirement search. */
public record RequirementSearchPage(
        KnowledgeSnapshotMetadata snapshot,
        RequirementSearchQuery query,
        List<RequirementVersionRecord> items,
        PageRequest pageRequest,
        int totalMatches,
        boolean hasMore) {

    public RequirementSearchPage {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(query, "query");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(pageRequest, "pageRequest");
        if (totalMatches < 0) {
            throw new IllegalArgumentException("totalMatches must be greater than or equal to zero");
        }
        if (items.size() > pageRequest.limit()) {
            throw new IllegalArgumentException("items must not exceed requested page limit");
        }
        boolean expectedHasMore = (long) pageRequest.offset() + items.size() < totalMatches;
        if (hasMore != expectedHasMore) {
            throw new IllegalArgumentException("hasMore is inconsistent with page bounds and totalMatches");
        }
    }

    /** Compatibility constructor for callers that predate query metadata retention. */
    public RequirementSearchPage(
            KnowledgeSnapshotMetadata snapshot,
            List<RequirementVersionRecord> items,
            PageRequest pageRequest,
            int totalMatches,
            boolean hasMore) {
        this(snapshot, RequirementSearchQuery.all(), items, pageRequest, totalMatches, hasMore);
    }
}
