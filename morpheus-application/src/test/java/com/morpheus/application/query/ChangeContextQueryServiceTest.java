package com.morpheus.application.query;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.traceability.ExternalTraceabilityAvailability;
import com.morpheus.application.traceability.ExternalTraceabilityView;
import com.morpheus.application.traceability.TraceabilitySubgraph;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.morpheus.application.query.PublishedSnapshotWorld.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeContextQueryServiceTest {
    private static final Set<TraceabilityRelationType> ALL_RELATIONS = Set.of();

    private final PublishedSnapshotWorld world = new PublishedSnapshotWorld();
    private final ChangeContextQueryService service = new ChangeContextQueryService(world.snapshots(),
            world.contentStore(), world.requirementStore(), world.traceabilityStore(), world.referenceStore());

    @Test
    void theContextGathersTheChangeItsChildrenItsAffectedRequirementsAndItsExternalLinks() {
        Specification specification = world.specification("spec");
        ChangeProposal change = world.change("change");
        ChangeProposal other = world.change("other");
        Constraint constraint = world.constraint(change.id());
        world.constraint(other.id());
        DesignDecision decision = world.decision(change.id());
        ImplementationTask task = world.task(change.id());
        RequirementVersionRecord affected = world.requirement(specification.id(), "R-1", "affected");
        TraceabilityEntityRef root = ref(TraceabilityEntityKind.CHANGE, change.id().value());
        TraceabilityLink resolvedLink = world.link(root, TraceabilityRelationType.AFFECTS,
                ref(TraceabilityEntityKind.REQUIREMENT, affected.entityVersion().entityIdentity()));
        TraceabilityLink unresolvedLink = world.link(root, TraceabilityRelationType.AFFECTS,
                ref(TraceabilityEntityKind.REQUIREMENT, world.next()));
        world.link(root, TraceabilityRelationType.AFFECTS, ref(TraceabilityEntityKind.SPECIFICATION,
                specification.id().value()));
        ExternalReference reference = world.reference(change.id().value());
        TraceabilityLink externalLink = world.link(root, TraceabilityRelationType.IMPLEMENTS,
                ref(TraceabilityEntityKind.EXTERNAL_REFERENCE, reference.id().value()));

        ChangeContextResult context = service.active(world.projectId(), change.id(), 2, ALL_RELATIONS).orElseThrow();

        assertEquals(world.active(), context.snapshot());
        assertEquals(Optional.of(change), context.change());
        assertEquals(List.of(constraint), context.constraints());
        assertEquals(List.of(decision), context.designDecisions());
        assertEquals(List.of(task), context.implementationTasks());
        assertEquals(List.of(resolvedLink, unresolvedLink), context.affectedRequirementLinks());
        assertEquals(List.of(affected), context.affectedRequirements());
        assertEquals(root, context.subgraph().start());
        assertEquals(4, context.subgraph().links().size());
        assertEquals(List.of(new ExternalTraceabilityView(externalLink, Optional.of(reference),
                ExternalTraceabilityAvailability.REFERENCE_UNVALIDATED)), context.externalLinks());
    }

    @Test
    void anAbsentChangeStillAnswersWithItsEmptyContextInTheSnapshot() {
        ChangeId absent = new ChangeId(world.next());

        ChangeContextResult context = service.active(world.projectId(), absent, 1,
                Set.of(TraceabilityRelationType.AFFECTS)).orElseThrow();

        assertTrue(context.change().isEmpty());
        assertEquals(absent, context.changeId());
        assertEquals(List.of(), context.affectedRequirements());
        assertEquals(List.of(), context.externalLinks());
        assertTrue(service.active(new ProjectSpecificationId(world.next()), absent, 1, ALL_RELATIONS).isEmpty());
    }

    @Test
    void aSnapshotContextReadsARetiredSnapshotAndRefusesAnUnpublishedOrUnknownOne() {
        ChangeProposal change = world.change("change");
        KnowledgeSnapshotMetadata retired = world.snapshot(KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata failed = world.snapshot(KnowledgeSnapshotState.FAILED);
        KnowledgeSnapshotId unknown = new KnowledgeSnapshotId(world.next());

        assertEquals(retired, service.snapshot(retired.id(), change.id(), 1, ALL_RELATIONS).snapshot());
        KnowledgeStoreException notPublished = assertThrows(KnowledgeStoreException.class,
                () -> service.snapshot(failed.id(), change.id(), 1, ALL_RELATIONS));
        assertEquals("change-context query requires an ACTIVE or RETIRED snapshot: " + failed.id() + " is FAILED",
                notPublished.getMessage());
        KnowledgeStoreException missing = assertThrows(KnowledgeStoreException.class,
                () -> service.snapshot(unknown, change.id(), 1, ALL_RELATIONS));
        assertEquals("unknown knowledge snapshot: " + unknown, missing.getMessage());
    }

    @Test
    void aDepthBelowOneAndAMissingProjectionAreRefusedByName() {
        ChangeId change = world.change("change").id();

        for (Executable call : List.<Executable>of(
                () -> service.active(world.projectId(), change, 0, ALL_RELATIONS),
                () -> service.snapshot(world.active().id(), change, -1, ALL_RELATIONS))) {
            IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, call);
            assertEquals("maxDepth must be greater than zero", refusal.getMessage());
        }
        world.withoutContent();
        KnowledgeStoreException noContent = assertThrows(KnowledgeStoreException.class,
                () -> service.active(world.projectId(), change, 1, ALL_RELATIONS));
        assertEquals("published snapshot has no business-content projection: " + world.active().id(),
                noContent.getMessage());
    }

    @Test
    void aResultRefusesEveryMemberThatDoesNotBelongToItsChangeOrItsSnapshot() {
        Specification specification = world.specification("spec");
        ChangeProposal change = world.change("change");
        ChangeProposal other = world.change("other");
        TraceabilityEntityRef root = ref(TraceabilityEntityKind.CHANGE, change.id().value());
        RequirementVersionRecord current = world.requirement(specification.id(), "R-1", "current");
        TraceabilityLink affects = world.link(root, TraceabilityRelationType.AFFECTS,
                ref(TraceabilityEntityKind.REQUIREMENT, current.entityVersion().entityIdentity()));
        TraceabilityLink implementsLink = world.link(root, TraceabilityRelationType.IMPLEMENTS,
                ref(TraceabilityEntityKind.REQUIREMENT, current.entityVersion().entityIdentity()));
        RequirementVersionRecord proposed = world.requirementIn(world.active().id(), specification.id(), "R-2",
                "proposed", TemporalState.PROPOSED);
        KnowledgeSnapshotMetadata retired = world.snapshot(KnowledgeSnapshotState.RETIRED);
        RequirementVersionRecord elsewhere = world.requirementIn(retired.id(), specification.id(), "R-3",
                "elsewhere", TemporalState.CURRENT);
        RequirementVersionRecord unbacked = world.requirement(specification.id(), "R-4", "unbacked");
        TraceabilitySubgraph subgraph = new TraceabilitySubgraph(root, List.of(root), List.of(affects),
                Optional.empty());
        ExternalReference reference = world.reference(change.id().value());
        TraceabilityLink outside = world.link(root, TraceabilityRelationType.IMPLEMENTS,
                ref(TraceabilityEntityKind.EXTERNAL_REFERENCE, reference.id().value()));
        ResultBuilder valid = new ResultBuilder(change.id(), subgraph);

        Map<String, Executable> refusals = Map.of(
                "change context subgraph must be rooted at the requested change identity",
                () -> valid.with(b -> b.changeId = other.id()).build(),
                "change context item must match requested change identity",
                () -> valid.with(b -> b.change = Optional.of(other)).build(),
                "affected requirement links must be direct CHANGE --AFFECTS--> REQUIREMENT links",
                () -> valid.with(b -> b.links = List.of(implementsLink)).build(),
                "affected requirement must belong to the result snapshot",
                () -> valid.with(b -> b.requirements = List.of(elsewhere)).build(),
                "affected requirement must be CURRENT",
                () -> valid.with(b -> b.requirements = List.of(proposed)).build(),
                "affected requirement must be backed by an AFFECTS link",
                () -> valid.with(b -> b.requirements = List.of(unbacked)).build(),
                "constraint belongs to another change",
                () -> valid.with(b -> b.constraints = List.of(world.constraint(other.id()))).build(),
                "design decision belongs to another change",
                () -> valid.with(b -> b.decisions = List.of(world.decision(other.id()))).build(),
                "implementation task belongs to another change",
                () -> valid.with(b -> b.tasks = List.of(world.task(other.id()))).build(),
                "external traceability views must belong to the returned subgraph",
                () -> valid.with(b -> b.external = List.of(new ExternalTraceabilityView(outside,
                        Optional.of(reference), ExternalTraceabilityAvailability.REFERENCE_UNVALIDATED))).build());

        for (Map.Entry<String, Executable> refusal : refusals.entrySet()) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, refusal.getValue(),
                    refusal.getKey());
            assertEquals(refusal.getKey(), thrown.getMessage());
        }
        ChangeContextResult accepted = valid.with(b -> b.requirements = List.of(current)).build();
        assertEquals(List.of(current), accepted.affectedRequirements());
        assertFalse(accepted.change().isPresent());
    }

    private final class ResultBuilder {
        private ChangeId changeId;
        private Optional<ChangeProposal> change = Optional.empty();
        private List<TraceabilityLink> links;
        private List<RequirementVersionRecord> requirements = List.of();
        private List<Constraint> constraints = List.of();
        private List<DesignDecision> decisions = List.of();
        private List<ImplementationTask> tasks = List.of();
        private List<ExternalTraceabilityView> external = List.of();
        private final TraceabilitySubgraph subgraph;

        private ResultBuilder(ChangeId changeId, TraceabilitySubgraph subgraph) {
            this.changeId = changeId;
            this.subgraph = subgraph;
            this.links = subgraph.links();
        }

        private ResultBuilder with(Consumer<ResultBuilder> edit) {
            ResultBuilder copy = new ResultBuilder(changeId, subgraph);
            edit.accept(copy);
            return copy;
        }

        private ChangeContextResult build() {
            return new ChangeContextResult(world.active(), changeId, change, links, requirements, constraints,
                    decisions, tasks, subgraph, external);
        }
    }
}
