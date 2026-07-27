package com.morpheus.application.store;

import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Technology-neutral snapshot-scoped persistence port for traceability links. */
public interface TraceabilityStore {
    void putLink(KnowledgeSnapshotId snapshotId, TraceabilityLink link);

    /** Persists one snapshot-scoped batch; adapters may override without changing link semantics. */
    default void putLinks(KnowledgeSnapshotId snapshotId, List<TraceabilityLink> links) {
        List.copyOf(links).forEach(link -> putLink(snapshotId, link));
    }

    Optional<TraceabilityLink> findLink(KnowledgeSnapshotId snapshotId, TraceabilityLinkId linkId);

    List<TraceabilityLink> outgoing(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef source,
            Set<TraceabilityRelationType> relationTypes);

    List<TraceabilityLink> incoming(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef target,
            Set<TraceabilityRelationType> relationTypes);
}
