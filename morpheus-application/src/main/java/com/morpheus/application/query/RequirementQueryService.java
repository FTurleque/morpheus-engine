package com.morpheus.application.query;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementPage;
import com.morpheus.application.store.RequirementPageCriteria;
import com.morpheus.application.store.RequirementPageOrder;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;

import java.util.Objects;
import java.util.Optional;

/** Deterministic snapshot-coherent lexical search over persisted CURRENT requirements. */
public final class RequirementQueryService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;

    public RequirementQueryService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
    }

    public Optional<RequirementSearchPage> findActive(
            ProjectSpecificationId projectId,
            RequirementSearchQuery query,
            PageRequest pageRequest) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(pageRequest, "pageRequest");

        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> findPublished(snapshot, query, pageRequest));
    }

    public RequirementSearchPage findSnapshot(
            KnowledgeSnapshotId snapshotId,
            RequirementSearchQuery query,
            PageRequest pageRequest) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(pageRequest, "pageRequest");

        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        requirePublished(snapshot);
        return findPublished(snapshot, query, pageRequest);
    }

    private RequirementSearchPage findPublished(
            KnowledgeSnapshotMetadata snapshot,
            RequirementSearchQuery query,
            PageRequest pageRequest) {
        RequirementPage page = requirementStore.pageCurrentRequirementVersions(
                snapshot.id(),
                RequirementPageCriteria.lexical(query.terms()),
                RequirementPageOrder.REQUIREMENT_ID,
                pageRequest.offset(),
                pageRequest.limit());
        return new RequirementSearchPage(
                snapshot, query, page.items(), pageRequest, page.totalMatches(), page.hasMoreAfter(pageRequest.offset()));
    }

    private void requirePublished(KnowledgeSnapshotMetadata snapshot) {
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException(
                    "requirement query requires an ACTIVE or RETIRED snapshot: "
                            + snapshot.id() + " is " + snapshot.state());
        }
    }
}
