package com.morpheus.application.query;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessContentQueryServiceTest {
    private final PublishedSnapshotWorld world = new PublishedSnapshotWorld();
    private final BusinessContentQueryService service =
            new BusinessContentQueryService(world.snapshots(), world.contentStore());

    @Test
    void aProjectWithoutAnActiveSnapshotAnswersNothingOnEveryActiveQuery() {
        ProjectSpecificationId unknown = new ProjectSpecificationId(world.next());
        ChangeId change = new ChangeId(world.next());
        PageRequest page = PageRequest.first(10);

        assertTrue(service.activeSpecification(unknown, new SpecificationId(world.next())).isEmpty());
        assertTrue(service.activeChange(unknown, change).isEmpty());
        assertTrue(service.listActiveChanges(unknown, page).isEmpty());
        assertTrue(service.activeConstraints(unknown, change, page).isEmpty());
        assertTrue(service.activeDesignDecisions(unknown, change, page).isEmpty());
        assertTrue(service.activeImplementationTasks(unknown, change, page).isEmpty());
        assertTrue(service.activeAcceptanceCriteria(unknown, page).isEmpty());
        assertTrue(service.activeAcceptanceCriteriaForChange(unknown, change, page).isEmpty());
        assertTrue(service.activeAcceptanceCriteriaForRequirement(unknown, new RequirementId(world.next()), page)
                .isEmpty());
    }

    @Test
    void aSpecificationAndAChangeAreFoundInTheActiveSnapshotOrReportedAbsentWithIt() {
        Specification specification = world.specification("spec");
        ChangeProposal change = world.change("change");

        SnapshotItemResult<Specification> found =
                service.activeSpecification(world.projectId(), specification.id()).orElseThrow();
        SnapshotItemResult<Specification> absent =
                service.activeSpecification(world.projectId(), new SpecificationId(world.next())).orElseThrow();
        SnapshotItemResult<ChangeProposal> foundChange =
                service.activeChange(world.projectId(), change.id()).orElseThrow();
        SnapshotItemResult<ChangeProposal> absentChange =
                service.activeChange(world.projectId(), new ChangeId(world.next())).orElseThrow();

        assertEquals(new SnapshotItemResult<>(world.active(), Optional.of(specification)), found);
        assertEquals(world.active(), absent.snapshot());
        assertTrue(absent.item().isEmpty());
        assertEquals(Optional.of(change), foundChange.item());
        assertTrue(absentChange.item().isEmpty());
    }

    @Test
    void aSnapshotSpecificationIsReadOnlyFromAPublishedSnapshot() {
        Specification specification = world.specification("spec");
        KnowledgeSnapshotMetadata retired = world.snapshot(KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata building = world.snapshot(KnowledgeSnapshotState.BUILDING);
        KnowledgeSnapshotId unknown = new KnowledgeSnapshotId(world.next());

        assertEquals(Optional.of(specification),
                service.snapshotSpecification(retired.id(), specification.id()).item());
        assertEquals(retired, service.snapshotSpecification(retired.id(), specification.id()).snapshot());
        KnowledgeStoreException notPublished = assertThrows(KnowledgeStoreException.class,
                () -> service.snapshotSpecification(building.id(), specification.id()));
        assertEquals("business-content query requires an ACTIVE or RETIRED snapshot: " + building.id()
                + " is BUILDING", notPublished.getMessage());
        KnowledgeStoreException missing = assertThrows(KnowledgeStoreException.class,
                () -> service.snapshotSpecification(unknown, specification.id()));
        assertEquals("unknown knowledge snapshot: " + unknown, missing.getMessage());
    }

    @Test
    void changesArePagedInIdentityOrderAndSayWhetherMoreFollow() {
        ChangeProposal first = world.change("a");
        ChangeProposal second = world.change("b");
        ChangeProposal third = world.change("c");

        SnapshotPage<ChangeProposal> head = service.listActiveChanges(world.projectId(), new PageRequest(0, 2))
                .orElseThrow();
        SnapshotPage<ChangeProposal> tail = service.listActiveChanges(world.projectId(), new PageRequest(2, 2))
                .orElseThrow();
        SnapshotPage<ChangeProposal> beyond = service.listActiveChanges(world.projectId(), new PageRequest(7, 2))
                .orElseThrow();

        assertEquals(List.of(first, second), head.items());
        assertEquals(3, head.totalMatches());
        assertTrue(head.hasMore());
        assertEquals(List.of(third), tail.items());
        assertFalse(tail.hasMore());
        assertEquals(List.of(), beyond.items());
        assertEquals(3, beyond.totalMatches());
        assertFalse(beyond.hasMore());
    }

    @Test
    void theChildrenOfAChangeAreThoseThatNameItInIdentityOrder() {
        ChangeProposal change = world.change("target");
        ChangeProposal other = world.change("other");
        Constraint firstConstraint = world.constraint(change.id());
        world.constraint(other.id());
        Constraint secondConstraint = world.constraint(change.id());
        DesignDecision decision = world.decision(change.id());
        world.decision(other.id());
        ImplementationTask task = world.task(change.id());
        world.task(other.id());
        PageRequest page = PageRequest.first(10);

        assertEquals(List.of(firstConstraint, secondConstraint),
                service.activeConstraints(world.projectId(), change.id(), page).orElseThrow().items());
        assertEquals(List.of(decision),
                service.activeDesignDecisions(world.projectId(), change.id(), page).orElseThrow().items());
        assertEquals(List.of(task),
                service.activeImplementationTasks(world.projectId(), change.id(), page).orElseThrow().items());
        SnapshotPage<Constraint> narrow =
                service.activeConstraints(world.projectId(), change.id(), PageRequest.first(1)).orElseThrow();
        assertEquals(List.of(firstConstraint), narrow.items());
        assertEquals(2, narrow.totalMatches());
        assertTrue(narrow.hasMore());
    }

    @Test
    void acceptanceCriteriaAreListedWholeOrByTheChangeOrTheRequirementTheyName() {
        ChangeProposal change = world.change("change");
        RequirementId requirement = new RequirementId(world.next());
        AcceptanceCriterion forChange = world.criterion(Optional.empty(), Optional.of(change.id()));
        AcceptanceCriterion forRequirement = world.criterion(Optional.of(requirement), Optional.empty());
        AcceptanceCriterion forBoth = world.criterion(Optional.of(requirement), Optional.of(change.id()));
        PageRequest page = PageRequest.first(10);

        assertEquals(List.of(forChange, forRequirement, forBoth),
                service.activeAcceptanceCriteria(world.projectId(), page).orElseThrow().items());
        assertEquals(List.of(forChange, forBoth),
                service.activeAcceptanceCriteriaForChange(world.projectId(), change.id(), page).orElseThrow().items());
        assertEquals(List.of(forRequirement, forBoth),
                service.activeAcceptanceCriteriaForRequirement(world.projectId(), requirement, page)
                        .orElseThrow().items());
        assertEquals(List.of(), service.activeAcceptanceCriteriaForRequirement(
                world.projectId(), new RequirementId(world.next()), page).orElseThrow().items());
    }

    @Test
    void aPublishedSnapshotWithoutItsBusinessContentIsRefusedByName() {
        world.withoutContent();

        KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class,
                () -> service.listActiveChanges(world.projectId(), PageRequest.first(1)));

        assertEquals("published snapshot has no business-content projection: " + world.active().id(),
                refusal.getMessage());
    }

    @Test
    void aSnapshotItemResultRequiresItsSnapshotAndAnExplicitAbsence() {
        NullPointerException noSnapshot = assertThrows(NullPointerException.class,
                () -> new SnapshotItemResult<>(null, Optional.empty()));
        assertEquals("snapshot", noSnapshot.getMessage());
        NullPointerException noItem = assertThrows(NullPointerException.class,
                () -> new SnapshotItemResult<String>(world.active(), null));
        assertEquals("item", noItem.getMessage());
    }
}
