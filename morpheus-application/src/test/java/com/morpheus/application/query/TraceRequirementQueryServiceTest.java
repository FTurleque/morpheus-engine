package com.morpheus.application.query;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.traceability.ExternalTraceabilityAvailability;
import com.morpheus.application.traceability.TraceRequirementResult;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.morpheus.application.query.PublishedSnapshotWorld.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceRequirementQueryServiceTest {
    private final PublishedSnapshotWorld world = new PublishedSnapshotWorld();
    private final TraceRequirementQueryService service = new TraceRequirementQueryService(
            world.snapshots(), world.requirementStore(), world.traceabilityStore(), world.referenceStore());

    @Test
    void theActiveTraceIsRootedAtTheCurrentRequirementAndJoinsItsExternalReferences() {
        Specification specification = world.specification("spec");
        RequirementVersionRecord requirement = world.requirement(specification.id(), "R-1", "traced");
        RequirementId requirementId = requirement.entityVersion().content().id();
        TraceabilityEntityRef root = ref(TraceabilityEntityKind.REQUIREMENT, requirementId.value());
        ExternalReference reference = world.reference(requirementId.value());
        TraceabilityLink external = world.link(root, TraceabilityRelationType.IMPLEMENTS,
                ref(TraceabilityEntityKind.EXTERNAL_REFERENCE, reference.id().value()));
        TraceabilityLink broken = world.link(root, TraceabilityRelationType.IMPLEMENTS,
                ref(TraceabilityEntityKind.EXTERNAL_REFERENCE, world.next()));
        TraceabilityLink refined = world.link(ref(TraceabilityEntityKind.SPECIFICATION, specification.id().value()),
                TraceabilityRelationType.REFINES, root);

        TraceRequirementResult trace = service.active(world.projectId(), requirementId, 1,
                Set.of(TraceabilityRelationType.IMPLEMENTS)).orElseThrow();
        TraceRequirementResult everything = service.active(world.projectId(), requirementId, 1, Set.of())
                .orElseThrow();

        assertEquals(requirement, trace.requirement());
        assertEquals(root, trace.subgraph().start());
        assertEquals(List.of(external, broken), trace.subgraph().links());
        assertEquals(List.of(ExternalTraceabilityAvailability.REFERENCE_UNVALIDATED,
                        ExternalTraceabilityAvailability.BROKEN_REFERENCE),
                trace.externalLinks().stream().map(view -> view.availability()).toList());
        assertEquals(Optional.of(reference), trace.externalLinks().getFirst().reference());
        assertEquals(List.of(external, broken, refined), everything.subgraph().links());
    }

    @Test
    void anUnknownRequirementOrAnInactiveProjectHasNoTrace() {
        RequirementId unknown = new RequirementId(world.next());

        assertTrue(service.active(world.projectId(), unknown, 1, Set.of()).isEmpty());
        assertTrue(service.active(new ProjectSpecificationId(world.next()), unknown, 1, Set.of()).isEmpty());
        assertTrue(service.snapshot(world.active().id(), unknown, 1, Set.of()).isEmpty());
    }

    @Test
    void aSnapshotTraceReadsTheRequirementOfThatSnapshotAndRefusesAnUnpublishedOne() {
        Specification specification = world.specification("spec");
        KnowledgeSnapshotMetadata retired = world.snapshot(KnowledgeSnapshotState.RETIRED);
        RequirementVersionRecord old = world.requirementIn(retired.id(), specification.id(), "R-1", "old",
                TemporalState.CURRENT);
        KnowledgeSnapshotMetadata validating = world.snapshot(KnowledgeSnapshotState.VALIDATING);
        RequirementId requirementId = old.entityVersion().content().id();

        TraceRequirementResult trace = service.snapshot(retired.id(), requirementId, 3, Set.of()).orElseThrow();

        assertEquals(retired, trace.snapshot());
        assertEquals(old, trace.requirement());
        KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class,
                () -> service.snapshot(validating.id(), requirementId, 3, Set.of()));
        assertEquals("trace(requirement) requires an ACTIVE or RETIRED snapshot: " + validating.id()
                + " is VALIDATING", refusal.getMessage());
        IllegalArgumentException depth = assertThrows(IllegalArgumentException.class,
                () -> service.active(world.projectId(), requirementId, 0, Set.of()));
        assertEquals("maxDepth must be greater than zero", depth.getMessage());
    }
}
