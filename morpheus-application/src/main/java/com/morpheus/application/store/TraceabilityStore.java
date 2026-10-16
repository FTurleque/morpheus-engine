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

    /**
     * Persists one snapshot-scoped batch, all of it or none of it.
     *
     * <p>A link already defined identically joins the snapshot without being rewritten, and a link listed twice
     * identically is stored once. A different definition under an identity already used - in the store or earlier in
     * the batch - refuses the whole batch with a {@link KnowledgeStoreException} that names the identity, and nothing
     * of the batch is stored. There is no default: storing the links one by one is not this contract, since it keeps
     * the links that precede the one that refuses.</p>
     */
    void putLinks(KnowledgeSnapshotId snapshotId, List<TraceabilityLink> links);

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
