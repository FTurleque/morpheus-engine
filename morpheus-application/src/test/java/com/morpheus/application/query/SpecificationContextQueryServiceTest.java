package com.morpheus.application.query;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.morpheus.application.query.PublishedSnapshotWorld.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecificationContextQueryServiceTest {
    private final PublishedSnapshotWorld world = new PublishedSnapshotWorld();
    private final SpecificationContextQueryService service = new SpecificationContextQueryService(
            world.snapshots(), world.contentStore(), world.requirementStore(), world.traceabilityStore());

    @Test
    void theContextPagesTheRequirementsAndKeepsTheScenariosOfThePageAndTheChangesOfTheWholeSpecification() {
        Specification specification = world.specification("spec");
        Specification otherSpecification = world.specification("other");
        RequirementVersionRecord first = world.requirement(specification.id(), "R-1", "first");
        RequirementVersionRecord second = world.requirement(specification.id(), "R-2", "second");
        RequirementVersionRecord offPage = world.requirement(specification.id(), "R-3", "third");
        RequirementVersionRecord foreign = world.requirement(otherSpecification.id(), "O-1", "foreign");
        Scenario secondScenario = world.scenario(Optional.of(requirementOf(second).id()));
        Scenario firstScenario = world.scenario(Optional.of(requirementOf(first).id()));
        world.scenario(Optional.of(requirementOf(offPage).id()));
        world.scenario(Optional.empty());
        ChangeProposal affectingOffPage = world.change("off-page");
        ChangeProposal affectingForeign = world.change("foreign");
        ChangeProposal affectingASpecification = world.change("spec-target");
        ChangeProposal affectingAnUnknownRequirement = world.change("unknown");
        ChangeProposal affectingFirst = world.change("first");
        world.change("unlinked");
        affects(affectingOffPage, TraceabilityEntityKind.REQUIREMENT, requirementOf(offPage).id().value());
        affects(affectingForeign, TraceabilityEntityKind.REQUIREMENT, requirementOf(foreign).id().value());
        affects(affectingASpecification, TraceabilityEntityKind.SPECIFICATION, specification.id().value());
        affects(affectingAnUnknownRequirement, TraceabilityEntityKind.REQUIREMENT, world.next());
        affects(affectingFirst, TraceabilityEntityKind.REQUIREMENT, requirementOf(first).id().value());

        SpecificationContextResult context = service.active(world.projectId(), specification.id(),
                PageRequest.first(2)).orElseThrow();

        assertEquals(world.active(), context.snapshot());
        assertEquals(specification, context.specification());
        assertEquals(List.of(requirementOf(first), requirementOf(second)), context.requirements().items());
        assertEquals(3, context.requirements().totalMatches());
        assertTrue(context.requirements().hasMore());
        assertEquals(List.of(secondScenario, firstScenario), context.scenarios());
        assertEquals(List.of(affectingOffPage, affectingFirst), context.changes());
    }

    @Test
    void theLastPageOfASpecificationSaysNoMoreFollow() {
        Specification specification = world.specification("spec");
        world.requirement(specification.id(), "R-1", "first");
        RequirementVersionRecord last = world.requirement(specification.id(), "R-2", "second");

        SpecificationContextResult context = service.active(world.projectId(), specification.id(),
                new PageRequest(1, 5)).orElseThrow();

        assertEquals(List.of(requirementOf(last)), context.requirements().items());
        assertFalse(context.requirements().hasMore());
        assertEquals(List.of(), context.changes());
    }

    @Test
    void anUnknownSpecificationOrAMissingProjectionIsRefusedByNameAndAnInactiveProjectHasNoContext() {
        SpecificationId unknown = new SpecificationId(world.next());

        KnowledgeStoreException notFound = assertThrows(KnowledgeStoreException.class,
                () -> service.active(world.projectId(), unknown, PageRequest.first(1)));
        assertEquals("specification not found: " + unknown, notFound.getMessage());
        assertTrue(service.active(new ProjectSpecificationId(world.next()), unknown, PageRequest.first(1)).isEmpty());

        world.withoutContent();
        KnowledgeStoreException noContent = assertThrows(KnowledgeStoreException.class,
                () -> service.active(world.projectId(), unknown, PageRequest.first(1)));
        assertEquals("published snapshot has no business-content projection: " + world.active().id(),
                noContent.getMessage());
    }

    @Test
    void aContextRefusesARequirementPageOfAnotherSnapshot() {
        Specification specification = world.specification("spec");
        KnowledgeSnapshotMetadata retired = world.snapshot(KnowledgeSnapshotState.RETIRED);
        SnapshotPage<Requirement> foreignPage = new SnapshotPage<>(retired, List.of(), PageRequest.first(1), 0, false);

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> new SpecificationContextResult(world.active(), specification, foreignPage, List.of(),
                        List.of()));

        assertEquals("requirements page belongs to another snapshot", refusal.getMessage());
    }

    private void affects(ChangeProposal change, TraceabilityEntityKind kind,
                         DomainIdentity target) {
        world.link(ref(TraceabilityEntityKind.CHANGE, change.id().value()), TraceabilityRelationType.AFFECTS,
                ref(kind, target));
    }

    private static Requirement requirementOf(RequirementVersionRecord record) {
        return record.entityVersion().content();
    }
}
