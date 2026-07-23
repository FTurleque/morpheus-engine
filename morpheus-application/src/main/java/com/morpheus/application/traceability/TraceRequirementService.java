package com.morpheus.application.traceability;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Final M4 query facade for an explainable bounded trace rooted at one requirement. */
public final class TraceRequirementService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;
    private final TraceabilityTraversalService traversalService;
    private final ExternalTraceabilityQueryService externalQueryService;

    public TraceRequirementService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore,
            ExternalReferenceStore externalReferenceStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        TraceabilityStore links = Objects.requireNonNull(traceabilityStore, "traceabilityStore");
        this.traversalService = new TraceabilityTraversalService(links);
        this.externalQueryService = new ExternalTraceabilityQueryService(
                links,
                Objects.requireNonNull(externalReferenceStore, "externalReferenceStore"));
    }

    public Optional<TraceRequirementResult> traceActive(
            ProjectSpecificationId projectId,
            RequirementId requirementId,
            int maxDepth,
            Set<TraceabilityRelationType> relationTypes) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(relationTypes, "relationTypes");

        return snapshotStore.activeSnapshot(projectId)
                .flatMap(snapshot -> tracePublished(snapshot, requirementId, maxDepth, relationTypes));
    }

    public Optional<TraceRequirementResult> traceSnapshot(
            KnowledgeSnapshotId snapshotId,
            RequirementId requirementId,
            int maxDepth,
            Set<TraceabilityRelationType> relationTypes) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(relationTypes, "relationTypes");

        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        requirePublished(snapshot);
        return tracePublished(snapshot, requirementId, maxDepth, relationTypes);
    }

    private Optional<TraceRequirementResult> tracePublished(
            KnowledgeSnapshotMetadata snapshot,
            RequirementId requirementId,
            int maxDepth,
            Set<TraceabilityRelationType> relationTypes) {
        return requirementStore.currentRequirement(snapshot.id(), requirementId.value())
                .map(requirement -> {
                    TraceabilityEntityRef root = new TraceabilityEntityRef(
                            TraceabilityEntityKind.REQUIREMENT,
                            requirementId.value());
                    TraceabilitySubgraph subgraph = traversalService.traverse(
                            snapshot.id(),
                            root,
                            maxDepth,
                            TraceabilityTraversalDirection.BIDIRECTIONAL,
                            relationTypes);
                    var externalLinks = subgraph.links().stream()
                            .filter(link -> link.target().kind() == TraceabilityEntityKind.EXTERNAL_REFERENCE)
                            .sorted(Comparator.comparing(link -> link.id()))
                            .map(link -> externalQueryService.inspect(snapshot.id(), link))
                            .toList();
                    return new TraceRequirementResult(snapshot, requirement, subgraph, externalLinks);
                });
    }

    private void requirePublished(KnowledgeSnapshotMetadata snapshot) {
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException(
                    "trace(requirement) requires an ACTIVE or RETIRED snapshot: "
                            + snapshot.id() + " is " + snapshot.state());
        }
    }
}
