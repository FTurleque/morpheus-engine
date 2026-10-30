package com.morpheus.application.quality;

import com.morpheus.application.query.PublishedSnapshotWorld;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.morpheus.application.query.PublishedSnapshotWorld.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A task is covered when its change AFFECTS a CURRENT requirement of the same snapshot; any other task is a finding
 * that names the task, its change and the snapshot.
 */
class TaskQualityServiceTest {
    private final PublishedSnapshotWorld world = new PublishedSnapshotWorld();
    private final TaskQualityService service = new TaskQualityService(
            world.snapshots(), world.contentStore(), world.requirementStore(), world.traceabilityStore());

    @Test
    void aTaskWhoseChangeAffectsACurrentRequirementIsCoveredAndAnyOtherIsAFinding() {
        Specification specification = world.specification("auth");
        var current = world.requirement(specification.id(), "R1", "shall login");
        var superseded = world.requirementIn(world.active().id(), specification.id(), "R2", "old",
                TemporalState.HISTORICAL);
        ChangeProposal covering = world.change("covering");
        ChangeProposal stale = world.change("stale");
        ChangeProposal unlinked = world.change("unlinked");
        world.link(ref(TraceabilityEntityKind.CHANGE, covering.id().value()), TraceabilityRelationType.AFFECTS,
                ref(TraceabilityEntityKind.REQUIREMENT, current.entityVersion().content().id().value()));
        world.link(ref(TraceabilityEntityKind.CHANGE, stale.id().value()), TraceabilityRelationType.AFFECTS,
                ref(TraceabilityEntityKind.REQUIREMENT, superseded.entityVersion().content().id().value()));
        world.link(ref(TraceabilityEntityKind.CHANGE, unlinked.id().value()), TraceabilityRelationType.AFFECTS,
                ref(TraceabilityEntityKind.SPECIFICATION, specification.id().value()));
        world.task(covering.id());
        ImplementationTask staleTask = world.task(stale.id());
        world.task(unlinked.id());

        TaskRequirementCoverage coverage = service.assessActive(world.projectId()).orElseThrow();

        assertEquals(3, coverage.totalTasks());
        assertEquals(1, coverage.coveredTasks());
        assertEquals(2, coverage.uncoveredTasks());
        assertEquals(1.0 / 3, coverage.coverageRatio(), 1e-9);
        QualityFinding first = coverage.findings().getFirst();
        assertEquals(QualityFindingCode.IMPLEMENTATION_TASK_WITHOUT_REQUIREMENT, first.code());
        assertEquals(staleTask.id().toString(), first.details().get("taskId"));
        assertEquals(stale.id().toString(), first.details().get("changeId"));
        assertEquals(world.active().id().toString(), first.details().get("snapshotId"));
    }

    @Test
    void aSnapshotWithoutTasksIsFullyCovered() {
        TaskRequirementCoverage coverage = service.assessSnapshot(world.active().id());

        assertEquals(0, coverage.totalTasks());
        assertEquals(1.0, coverage.coverageRatio());
        assertEquals(List.of(), coverage.findings());
    }

    @Test
    void aProjectWithoutAnActiveSnapshotHasNoAssessment() {
        assertEquals(Optional.empty(), service.assessActive(ProjectSpecificationId.generate()));
    }

    @Test
    void aRetiredSnapshotIsAssessedAndAReadyOneIsRefusedByName() {
        KnowledgeSnapshotMetadata retired = world.snapshot(KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata ready = world.snapshot(KnowledgeSnapshotState.READY);

        assertEquals(retired, service.assessSnapshot(retired.id()).snapshot());
        KnowledgeStoreException refused = assertThrows(KnowledgeStoreException.class,
                () -> service.assessSnapshot(ready.id()));
        assertEquals("quality analysis requires an ACTIVE or RETIRED snapshot: " + ready.id() + " is READY",
                refused.getMessage());
    }

    @Test
    void anUnknownSnapshotOrOneWithoutContentIsRefusedByName() {
        KnowledgeSnapshotId unknown = KnowledgeSnapshotId.generate();
        KnowledgeStoreException missing = assertThrows(KnowledgeStoreException.class,
                () -> service.assessSnapshot(unknown));
        world.withoutContent();
        KnowledgeStoreException noContent = assertThrows(KnowledgeStoreException.class,
                () -> service.assessSnapshot(world.active().id()));

        assertEquals("unknown knowledge snapshot: " + unknown, missing.getMessage());
        assertEquals("published snapshot has no business-content projection: " + world.active().id(),
                noContent.getMessage());
    }
}
