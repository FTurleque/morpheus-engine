package com.morpheus.application.quality;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deterministic quality analysis of implementation-task coverage by CURRENT requirements. */
public final class TaskQualityService {
    private static final Comparator<ImplementationTask> TASK_ORDER = Comparator.comparing(ImplementationTask::id);

    private final SpecificationKnowledgeStore snapshotStore;
    private final SnapshotBusinessContentStore contentStore;
    private final VersionedRequirementStore requirementStore;
    private final TraceabilityStore traceabilityStore;

    public TaskQualityService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        this.traceabilityStore = Objects.requireNonNull(traceabilityStore, "traceabilityStore");
    }

    public Optional<TaskRequirementCoverage> assessActive(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return snapshotStore.activeSnapshot(projectId).map(this::assessPublished);
    }

    public TaskRequirementCoverage assessSnapshot(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        requirePublished(snapshot);
        return assessPublished(snapshot);
    }

    private TaskRequirementCoverage assessPublished(KnowledgeSnapshotMetadata snapshot) {
        SnapshotBusinessContent content = contentStore.findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.id()));

        Set<DomainIdentity> currentRequirementIdentities = requirementStore.listRequirementVersions(snapshot.id()).stream()
                .filter(record -> record.entityVersion().temporalState() == TemporalState.CURRENT)
                .map(RequirementVersionRecord::entityVersion)
                .map(version -> version.content().id().value())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        List<ImplementationTask> tasks = content.tasks().stream().sorted(TASK_ORDER).toList();
        List<QualityFinding> findings = new ArrayList<>();
        int covered = 0;

        for (ImplementationTask task : tasks) {
            TraceabilityEntityRef change = new TraceabilityEntityRef(
                    TraceabilityEntityKind.CHANGE,
                    task.changeId().value());

            boolean hasCurrentRequirement = traceabilityStore
                    .outgoing(snapshot.id(), change, Set.of(TraceabilityRelationType.AFFECTS))
                    .stream()
                    .map(link -> link.target())
                    .anyMatch(target -> target.kind() == TraceabilityEntityKind.REQUIREMENT
                            && currentRequirementIdentities.contains(target.identity()));

            if (hasCurrentRequirement) {
                covered++;
                continue;
            }

            findings.add(new QualityFinding(
                    QualityFindingCode.IMPLEMENTATION_TASK_WITHOUT_REQUIREMENT,
                    DiagnosticSeverity.WARNING,
                    QualityEvidenceKind.DETERMINISTIC,
                    new TraceabilityEntityRef(TraceabilityEntityKind.IMPLEMENTATION_TASK, task.id().value()),
                    "Implementation task is not covered by a CURRENT requirement through its owning change",
                    Map.of(
                            "taskId", task.id().toString(),
                            "changeId", task.changeId().toString(),
                            "snapshotId", snapshot.id().toString()),
                    Optional.empty(),
                    List.of(task.provenance().evidenceId())));
        }

        int total = tasks.size();
        int uncovered = total - covered;
        double ratio = total == 0 ? 1.0 : (double) covered / total;
        return new TaskRequirementCoverage(snapshot, total, covered, uncovered, ratio, findings);
    }

    private void requirePublished(KnowledgeSnapshotMetadata snapshot) {
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException(
                    "quality analysis requires an ACTIVE or RETIRED snapshot: "
                            + snapshot.id() + " is " + snapshot.state());
        }
    }
}
