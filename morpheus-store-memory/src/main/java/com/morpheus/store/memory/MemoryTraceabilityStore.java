package com.morpheus.store.memory;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Reference in-memory adapter for snapshot-scoped traceability persistence. */
public final class MemoryTraceabilityStore implements TraceabilityStore {
    private final SpecificationKnowledgeStore snapshots;
    private final Map<TraceabilityLinkId, TraceabilityLink> definitions = new HashMap<>();
    private final Map<KnowledgeSnapshotId, Set<TraceabilityLinkId>> memberships = new HashMap<>();

    public MemoryTraceabilityStore(SpecificationKnowledgeStore snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    @Override
    public synchronized void putLink(KnowledgeSnapshotId snapshotId, TraceabilityLink link) {
        requireSnapshot(snapshotId);
        Objects.requireNonNull(link, "link");

        TraceabilityLink existing = definitions.get(link.id());
        if (existing != null && !existing.equals(link)) {
            throw new KnowledgeStoreException("traceability link identity collision: " + link.id());
        }
        definitions.putIfAbsent(link.id(), link);
        memberships.computeIfAbsent(snapshotId, ignored -> new HashSet<>()).add(link.id());
    }

    @Override
    public synchronized Optional<TraceabilityLink> findLink(
            KnowledgeSnapshotId snapshotId,
            TraceabilityLinkId linkId) {
        requireSnapshot(snapshotId);
        Objects.requireNonNull(linkId, "linkId");
        if (!memberships.getOrDefault(snapshotId, Set.of()).contains(linkId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(linkId));
    }

    @Override
    public synchronized List<TraceabilityLink> outgoing(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef source,
            Set<TraceabilityRelationType> relationTypes) {
        requireSnapshot(snapshotId);
        Objects.requireNonNull(source, "source");
        Set<TraceabilityRelationType> filter = immutableFilter(relationTypes);
        return links(snapshotId).stream()
                .filter(link -> link.source().equals(source))
                .filter(link -> filter.isEmpty() || filter.contains(link.relationType()))
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
    }

    @Override
    public synchronized List<TraceabilityLink> incoming(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef target,
            Set<TraceabilityRelationType> relationTypes) {
        requireSnapshot(snapshotId);
        Objects.requireNonNull(target, "target");
        Set<TraceabilityRelationType> filter = immutableFilter(relationTypes);
        return links(snapshotId).stream()
                .filter(link -> link.target().equals(target))
                .filter(link -> filter.isEmpty() || filter.contains(link.relationType()))
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
    }

    private List<TraceabilityLink> links(KnowledgeSnapshotId snapshotId) {
        return memberships.getOrDefault(snapshotId, Set.of()).stream()
                .map(definitions::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private Set<TraceabilityRelationType> immutableFilter(Set<TraceabilityRelationType> relationTypes) {
        return Set.copyOf(Objects.requireNonNull(relationTypes, "relationTypes"));
    }

    private void requireSnapshot(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        if (snapshots.findSnapshot(snapshotId).isEmpty()) {
            throw new KnowledgeStoreException("snapshot not found for traceability link: " + snapshotId);
        }
    }
}
