package com.morpheus.architecture;

import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.orchestration.ChangeLifecycleObservation;
import com.morpheus.application.orchestration.ChangeLifecycleObservationSource;
import com.morpheus.application.orchestration.ChangeOrchestrationState;
import com.morpheus.application.orchestration.ChangeOrchestrationStateService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationState;
import com.morpheus.application.quality.QualityFactValue;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemoryExternalReferenceStore;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarvisOrchestrationContractTest {
    private static final Instant T0 = Instant.parse("2026-07-24T16:00:00Z");

    @Test
    void stateNeverInfersLifecycleAndSeparatesMissingFromUnavailableFacts() {
        Fixture fixture = seed();

        ChangeOrchestrationState result = fixture.stateService().active(
                        fixture.projectId(), fixture.change().id(), ChangeLifecycleObservation.unavailable())
                .orElseThrow();

        assertEquals(ChangeLifecycleObservationSource.UNAVAILABLE, result.lifecycle().source());
        assertTrue(result.lifecycle().state().isEmpty());
        assertTrue(result.nextAllowedTransitions().isEmpty());
        assertTrue(result.transitionEvaluations().isEmpty());
        assertEquals(QualityFactValue.TRUE, result.observableFacts().get("requirementsIdentified"));
        assertEquals(QualityFactValue.FALSE, result.observableFacts().get("acceptanceCriteriaDefined"));
        assertTrue(result.missingArtifacts().contains("acceptanceCriteria"));
        assertFalse(result.unavailableFacts().contains("acceptanceCriteriaDefined"));
        assertEquals("AVAILABLE", result.acceptanceCriteria().status());
        assertEquals(0, result.acceptanceCriteria().observedCount());
        assertEquals("UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED", result.blockingConstraints().status());
        assertFalse(result.persisted());
        assertTrue(fixture.completedTask().completed(), "completed task must not become lifecycle state");
    }

    @Test
    void stateExposesApplicableConstraintsAndBothKindsOfUnresolvedLinks() {
        Fixture fixture = seed();

        ChangeOrchestrationState result = fixture.stateService().active(
                        fixture.projectId(), fixture.change().id(), ChangeLifecycleObservation.unavailable())
                .orElseThrow();

        assertEquals(1, result.applicableConstraints().size());
        assertEquals("Preserve auditability", result.applicableConstraints().getFirst().statement());
        assertEquals(2, result.unresolvedLinks().size());
        assertEquals(List.of("EXTERNAL_REFERENCE", "TRACEABILITY"),
                result.unresolvedLinks().stream().map(ChangeOrchestrationState.UnresolvedLinkView::kind).toList());
    }

    @Test
    void draftToProposedIsAllowedAndReportedAsNextAllowedTransition() {
        Fixture fixture = seed();
        ChangeLifecycleObservation lifecycle = ChangeLifecycleObservation.callerSupplied(
                ChangeLifecycleState.DRAFT, Optional.empty());

        ChangeOrchestrationState state = fixture.stateService().active(
                        fixture.projectId(), fixture.change().id(), lifecycle)
                .orElseThrow();
        var evaluation = fixture.transitionService().evaluateActive(
                        fixture.projectId(),
                        ChangeLifecycle.of(fixture.change().id(), ChangeLifecycleState.DRAFT),
                        ChangeLifecycleState.PROPOSED,
                        ChangeLifecyclePolicy.forwardOnly(),
                        Optional.empty())
                .orElseThrow();

        assertEquals(ChangeTransitionEvaluationState.ALLOWED, evaluation.state());
        assertTrue(state.nextAllowedTransitions().contains(ChangeLifecycleState.PROPOSED));
    }

    @Test
    void proposedToSpecifiedRemainsUnknownOnlyForFactsStillUnavailableInM15() {
        Fixture fixture = seed();

        var evaluation = fixture.transitionService().evaluateActive(
                        fixture.projectId(),
                        ChangeLifecycle.of(fixture.change().id(), ChangeLifecycleState.PROPOSED),
                        ChangeLifecycleState.SPECIFIED,
                        ChangeLifecyclePolicy.forwardOnly(),
                        Optional.empty())
                .orElseThrow();

        assertEquals(ChangeTransitionEvaluationState.UNKNOWN, evaluation.state());
        assertEquals(List.of("criticalConstraintsKnown"), evaluation.unavailableRequiredFacts());
        assertTrue(evaluation.blockers().isEmpty());
    }

    @Test
    void abandonmentWithoutReasonRequiresInputRatherThanMutatingOrGuessing() {
        Fixture fixture = seed();

        var evaluation = fixture.transitionService().evaluateActive(
                        fixture.projectId(),
                        ChangeLifecycle.of(fixture.change().id(), ChangeLifecycleState.DRAFT),
                        ChangeLifecycleState.ABANDONED,
                        ChangeLifecyclePolicy.forwardOnly(),
                        Optional.empty())
                .orElseThrow();

        assertEquals(ChangeTransitionEvaluationState.REQUIRES_INPUT, evaluation.state());
        assertTrue(evaluation.blockers().stream().anyMatch(blocker -> blocker.name().equals("ABANDONMENT_REASON_REQUIRED")));
        assertEquals(ChangeLifecycleState.DRAFT,
                fixture.stateService().active(
                                fixture.projectId(),
                                fixture.change().id(),
                                ChangeLifecycleObservation.callerSupplied(ChangeLifecycleState.DRAFT, Optional.empty()))
                        .orElseThrow().lifecycle().state().orElseThrow());
    }

    private Fixture seed() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        RequirementId requirementId = RequirementId.generate();
        ExternalReferenceId externalReferenceId = ExternalReferenceId.generate();
        Evidence evidence = new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("openspec/change.md"),
                Optional.empty(),
                Optional.of("sha256:m15"));
        Provenance provenance = new Provenance(
                new ProviderId("m15-fixture"),
                Optional.of("1"),
                SourceLocator.file("openspec/change.md"),
                Optional.of("m15"),
                Optional.of("revision-m15"),
                evidence.id());
        Specification specification = new Specification(
                specificationId, projectId, "m15", "M15", Optional.empty(), provenance);
        Requirement requirement = new Requirement(
                requirementId,
                specificationId,
                Optional.of("REQ-M15"),
                "M15 orchestration",
                "JARVIS consumes explicit MORPHEUS acceptance availability facts",
                provenance);
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(),
                projectId,
                Optional.of("m15-orchestration"),
                "Expose acceptance availability",
                "Expose acceptance facts without inventing blocking semantics",
                List.of("Read-only surface"),
                List.of(),
                List.of(),
                provenance);
        Constraint constraint = new Constraint(
                ConstraintId.generate(), change.id(), "Preserve auditability", provenance);
        ImplementationTask completedTask = new ImplementationTask(
                TaskId.generate(), change.id(), Optional.of("TASK-M15-1"), "Implement API", true, provenance);

        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        MemoryTraceabilityStore traceability = new MemoryTraceabilityStore(core);
        MemoryExternalReferenceStore externalReferences = new MemoryExternalReferenceStore(core);

        core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-m15")));
        core.putSpecificationVersion(new SpecificationVersion(
                versionId,
                projectId,
                Optional.of(1L),
                Optional.of("provider-v1"),
                Optional.of("revision-m15"),
                T0,
                Optional.empty()));
        core.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("revision-m15"),
                T0));
        core.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        core.putRequirementVersion(new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        EntityVersionId.generate(),
                        requirementId.value(),
                        versionId,
                        TemporalState.CURRENT,
                        requirement)));
        content.putSnapshotContent(new SnapshotBusinessContent(
                snapshotId,
                versionId,
                List.of(specification),
                List.of(),
                List.of(change),
                List.of(constraint),
                List.of(),
                List.of(completedTask),
                List.of(evidence)));
        traceability.putLink(snapshotId, new TraceabilityLink(
                TraceabilityLinkId.generate(),
                new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, change.id().value()),
                TraceabilityRelationType.AFFECTS,
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirementId.value()),
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(evidence.id()),
                T0));
        traceability.putLink(snapshotId, new TraceabilityLink(
                TraceabilityLinkId.generate(),
                new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, change.id().value()),
                TraceabilityRelationType.LINKS_TO_TEST,
                new TraceabilityEntityRef(TraceabilityEntityKind.EXTERNAL_REFERENCE, externalReferenceId.value()),
                TraceabilityLinkOrigin.EXPLICIT,
                TraceabilityResolutionState.UNRESOLVED,
                Optional.empty(),
                Set.of(evidence.id()),
                T0));
        externalReferences.putReference(snapshotId, ExternalReference.unvalidated(
                externalReferenceId,
                change.id().value(),
                new ExternalReferenceTarget(
                        "MINOS",
                        Optional.of("morpheus-engine"),
                        "SYMBOL",
                        "com.morpheus.Missing",
                        Optional.empty()),
                Optional.of(provenance)));
        core.activateSnapshot(snapshotId, Optional.empty());

        return new Fixture(
                projectId,
                change,
                completedTask,
                new ChangeOrchestrationStateService(core, content, core, traceability, externalReferences),
                new ChangeTransitionEvaluationService(core, content, core, traceability));
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            ChangeProposal change,
            ImplementationTask completedTask,
            ChangeOrchestrationStateService stateService,
            ChangeTransitionEvaluationService transitionService) {
    }
}
