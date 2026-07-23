package com.morpheus.application.query;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.temporal.TemporalState;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic snapshot-coherent lexical search over persisted CURRENT requirements. */
public final class RequirementQueryService {
    private static final Comparator<RequirementVersionRecord> REQUIREMENT_ORDER = Comparator
            .comparing(record -> record.entityVersion().content().id());

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
        List<RequirementVersionRecord> matches = requirementStore.listRequirementVersions(snapshot.id()).stream()
                .filter(record -> record.entityVersion().temporalState() == TemporalState.CURRENT)
                .filter(record -> matches(record.entityVersion().content(), query))
                .sorted(REQUIREMENT_ORDER)
                .toList();

        int totalMatches = matches.size();
        int from = Math.min(pageRequest.offset(), totalMatches);
        long requestedEnd = (long) from + pageRequest.limit();
        int to = (int) Math.min(requestedEnd, totalMatches);
        List<RequirementVersionRecord> items = matches.subList(from, to);
        boolean hasMore = to < totalMatches;

        return new RequirementSearchPage(snapshot, items, pageRequest, totalMatches, hasMore);
    }

    private boolean matches(Requirement requirement, RequirementSearchQuery query) {
        List<String> terms = query.terms();
        if (terms.isEmpty()) {
            return true;
        }

        String corpus = RequirementSearchQuery.normalize(String.join(
                " ",
                requirement.key().orElse(""),
                requirement.title(),
                requirement.statement()));
        return terms.stream().allMatch(corpus::contains);
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
