package com.morpheus.application.history;

import com.morpheus.application.store.RequirementPage;
import com.morpheus.application.store.RequirementPageCriteria;
import com.morpheus.application.store.RequirementPageOrder;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;

import java.util.Objects;
import java.util.Optional;

/** Reads the CURRENT requirement projection of one explicitly addressed published snapshot. */
public final class HistoricalRequirementQueryService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;

    public HistoricalRequirementQueryService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
    }

    /** One page of the CURRENT requirements of a published snapshot, in entity identity order, with their total. */
    public RequirementPage requirements(KnowledgeSnapshotId snapshotId, int offset, int limit) {
        requirePublished(snapshotId);
        return requirementStore.pageCurrentRequirementVersions(
                snapshotId, RequirementPageCriteria.all(), RequirementPageOrder.ENTITY_IDENTITY, offset, limit);
    }

    public Optional<RequirementVersionRecord> requirement(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity entityIdentity) {
        requirePublished(snapshotId);
        return requirementStore.currentRequirement(
                snapshotId,
                Objects.requireNonNull(entityIdentity, "entityIdentity"));
    }

    private KnowledgeSnapshotMetadata requirePublished(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new PublishedHistoryException("snapshot not found: " + snapshotId));
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new PublishedHistoryException(
                    "historical query requires ACTIVE or RETIRED snapshot but was "
                            + snapshot.state() + ": " + snapshotId);
        }
        if (requirementStore.findSnapshotVersion(snapshotId).isEmpty()) {
            throw new PublishedHistoryException("published snapshot has no specification version binding: " + snapshotId);
        }
        return snapshot;
    }
}