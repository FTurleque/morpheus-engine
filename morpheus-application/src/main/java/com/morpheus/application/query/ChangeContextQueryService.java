package com.morpheus.application.query;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.application.traceability.ExternalTraceabilityQueryService;
import com.morpheus.application.traceability.ExternalTraceabilityView;
import com.morpheus.application.traceability.TraceabilitySubgraph;
import com.morpheus.application.traceability.TraceabilityTraversalDirection;
import com.morpheus.application.traceability.TraceabilityTraversalService;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Deterministic snapshot-scoped aggregation for get_change_context. */
public final class ChangeContextQueryService {
    private static final Set<TraceabilityRelationType> AFFECTS_ONLY = Set.of(TraceabilityRelationType.AFFECTS);

    private final SpecificationKnowledgeStore snapshotStore;
    private final SnapshotBusinessContentStore contentStore;
    private final VersionedRequirementStore requirementStore;
    private final TraceabilityTraversalService traversalService;
    private final ExternalTraceabilityQueryService externalQueryService;

    public ChangeContextQueryService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore,
            ExternalReferenceStore externalReferenceStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        TraceabilityStore links = Objects.requireNonNull(traceabilityStore, "traceabilityStore");
        this.traversalService = new TraceabilityTraversalService(links);
        this.externalQueryService = new ExternalTraceabilityQueryService(
                links,
                Objects.requireNonNull(externalReferenceStore, "externalReferenceStore"));
    }

    public Optional<ChangeContextResult> active(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            int maxDepth,
            Set<TraceabilityRelationType> relationTypes) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(relationTypes, "relationTypes");
        requirePositiveDepth(maxDepth);

        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> context(snapshot, changeId, maxDepth, relationTypes));
    }

    public ChangeContextResult snapshot(
            KnowledgeSnapshotId snapshotId,
            ChangeId changeId,
            int maxDepth,
            Set<TraceabilityRelationType> relationTypes) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(relationTypes, "relationTypes");
        requirePositiveDepth(maxDepth);
        return context(requirePublished(snapshotId), changeId, maxDepth, relationTypes);
    }

    private ChangeContextResult context(
            KnowledgeSnapshotMetadata snapshot,
            ChangeId changeId,
            int maxDepth,
            Set<TraceabilityRelationType> relationTypes) {
        SnapshotBusinessContent content = contentStore.findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.id()));

        Optional<ChangeProposal> change = content.changes().stream()
                .filter(candidate -> candidate.id().equals(changeId))
                .findFirst();

        List<Constraint> constraints = content.constraints().stream()
                .filter(candidate -> candidate.changeId().equals(changeId))
                .sorted(Comparator.comparing(Constraint::id))
                .toList();
        List<DesignDecision> decisions = content.designDecisions().stream()
                .filter(candidate -> candidate.changeId().equals(changeId))
                .sorted(Comparator.comparing(DesignDecision::id))
                .toList();
        List<ImplementationTask> tasks = content.tasks().stream()
                .filter(candidate -> candidate.changeId().equals(changeId))
                .sorted(Comparator.comparing(ImplementationTask::id))
                .toList();

        TraceabilityEntityRef root = new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, changeId.value());
        List<TraceabilityLink> affectedLinks = traversalService.direct(
                        snapshot.id(), root, TraceabilityTraversalDirection.OUTGOING, AFFECTS_ONLY).stream()
                .filter(link -> link.target().kind() == TraceabilityEntityKind.REQUIREMENT)
                .sorted(Comparator.comparing(TraceabilityLink::id))
                .toList();

        Map<com.morpheus.domain.requirement.RequirementId, RequirementVersionRecord> affectedById = new TreeMap<>();
        for (TraceabilityLink link : affectedLinks) {
            var requirementId = new com.morpheus.domain.requirement.RequirementId(link.target().identity());
            requirementStore.currentRequirement(snapshot.id(), requirementId.value())
                    .ifPresent(record -> affectedById.put(requirementId, record));
        }

        TraceabilitySubgraph subgraph = traversalService.traverse(
                snapshot.id(),
                root,
                maxDepth,
                TraceabilityTraversalDirection.BIDIRECTIONAL,
                relationTypes);
        List<ExternalTraceabilityView> externalLinks = subgraph.links().stream()
                .filter(link -> link.target().kind() == TraceabilityEntityKind.EXTERNAL_REFERENCE)
                .sorted(Comparator.comparing(TraceabilityLink::id))
                .map(link -> externalQueryService.inspect(snapshot.id(), link))
                .toList();

        return new ChangeContextResult(
                snapshot,
                changeId,
                change,
                affectedLinks,
                List.copyOf(affectedById.values()),
                constraints,
                decisions,
                tasks,
                subgraph,
                externalLinks);
    }

    private KnowledgeSnapshotMetadata requirePublished(KnowledgeSnapshotId snapshotId) {
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException(
                    "change-context query requires an ACTIVE or RETIRED snapshot: "
                            + snapshot.id() + " is " + snapshot.state());
        }
        return snapshot;
    }

    private void requirePositiveDepth(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be greater than zero");
        }
    }
}
