package com.morpheus.architecture;

import com.morpheus.application.lifecycle.ChangeLifecycleBlocker;
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
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingPolicy;
import com.morpheus.domain.constraint.ConstraintEvaluationState;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarvisOrchestrationContractTest {
    private static final Instant T0 = Instant.parse("2026-07-24T16:00:00Z");

    @Test
    void stateNeverInfersLifecycleAndKeepsUnknownConstraintSemanticsExplicit() {
        Fixture fixture = seed(ConstraintMode.UNKNOWN);

        ChangeOrchestrationState result = fixture.stateService().active(
                        fixture.projectId(), fixture.change().id(), ChangeLifecycleObservation.unavailable())
                .orElseThrow();

        assertEquals(ChangeLifecycleObservationSource.UNAVAILABLE, result.lifecycle().source());
        assertTrue(result.lifecycle().state().isEmpty());
        assertTrue(result.nextAllowedTransitions().isEmpty());
        assertTrue(result.transitionEvaluations().isEmpty());
        assertEquals(QualityFactValue.TRUE, result.observableFacts().get("requirementsIdentified"));
        assertEquals(QualityFactValue.UNAVAILABLE, result.observableFacts().get("criticalConstraintsKnown"));
        assertEquals(QualityFactValue.FALSE, result.observableFacts().get("acceptanceCriteriaDefined"));
        assertTrue(result.missingArtifacts().contains("acceptanceCriteria"));
        assertFalse(result.unavailableFacts().contains("acceptanceCriteriaDefined"));
        assertTrue(result.unavailableFacts().contains("blockingConstraints"));
        assertEquals("AVAILABLE", result.acceptanceCriteria().status());
        assertEquals(0, result.acceptanceCriteria().observedCount());
        assertEquals("UNKNOWN", result.blockingConstraints().status());
        assertFalse(result.persisted());
        assertTrue(fixture.completedTask().completed(), "completed task must not become lifecycle state");
    }

    @Test
    void stateExposesConstraintPolicySeverityAndEvidence() {
        Fixture fixture = seed(ConstraintMode.BLOCKING_VERIFYING);

        ChangeOrchestrationState result = fixture.stateService().active(
                        fixture.projectId(), fixture.change().id(), ChangeLifecycleObservation.unavailable())
                .orElseThrow();

        assertEquals(1, result.applicableConstraints().size());
        var constraint = result.applicableConstraints().getFirst();
        assertEquals("Preserve auditability", constraint.statement());
        assertEquals("APPLICABLE", constraint.applicability());
        assertEquals("CRITICAL", constraint.severity());
        assertEquals("VIOLATED", constraint.satisfaction());
        assertEquals("BLOCK_WHEN_VIOLATED", constraint.blockingMode());
        assertEquals(List.of("VERIFYING"), constraint.blockingTargets());
        assertEquals(1, constraint.supportingEvidenceIds().size());
        assertEquals("AVAILABLE", result.blockingConstraints().status());
        assertEquals(1, result.blockingConstraints().observedCount());
        assertEquals(2, result.unresolvedLinks().size());
        assertEquals(List.of("EXTERNAL_REFERENCE", "TRACEABILITY"),
                result.unresolvedLinks().stream().map(ChangeOrchestrationState.UnresolvedLinkView::kind).toList());
    }

    @Test
    void unknownConstraintNeverBecomesBlockedOrSilentlyAllowed() {
        Fixture fixture = seed(ConstraintMode.UNKNOWN);

        var evaluation = fixture.transitionService().evaluateActive(
                        fixture.projectId(),
                        ChangeLifecycle.of(fixture.change().id(), ChangeLifecycleState.DRAFT),
                        ChangeLifecycleState.PROPOSED,
                        ChangeLifecyclePolicy.forwardOnly(),
                        Optional.empty())
                .orElseThrow();

        assertEquals(ChangeTransitionEvaluationState.UNKNOWN, evaluation.state());
        assertTrue(evaluation.blockers().isEmpty());
        assertEquals(List.of("blockingConstraints"), evaluation.unavailableRequiredFacts());
        assertEquals(ConstraintEvaluationState.UNKNOWN, evaluation.constraintEvaluations().getFirst().state());
    }

    @Test
    void explicitNonBlockingConstraintKeepsAllowedTransitionAllowed() {
        Fixture fixture = seed(ConstraintMode.NON_BLOCKING);
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
        assertEquals(ConstraintEvaluationState.NON_BLOCKING, evaluation.constraintEvaluations().getFirst().state());
        assertTrue(state.nextAllowedTransitions().contains(ChangeLifecycleState.PROPOSED));
    }

    @Test
    void targetedViolatedConstraintBlocksAndExplainsTransition() {
        Fixture fixture = seed(ConstraintMode.BLOCKING_VERIFYING);

        var evaluation = fixture.transitionService().evaluateActive(
                        fixture.projectId(),
                        ChangeLifecycle.of(fixture.change().id(), ChangeLifecycleState.IMPLEMENTING),
                        ChangeLifecycleState.VERIFYING,
                        ChangeLifecyclePolicy.forwardOnly(),
                        Optional.empty())
                .orElseThrow();

        assertEquals(ChangeTransitionEvaluationState.BLOCKED, evaluation.state());
        assertTrue(evaluation.blockers().contains(ChangeLifecycleBlocker.BLOCKING_CONSTRAINT));
        assertEquals(ConstraintEvaluationState.BLOCKING, evaluation.constraintEvaluations().getFirst().state());
        assertTrue(evaluation.constraintEvaluations().getFirst().reason().contains("VERIFYING"));
        assertEquals(1, evaluation.constraintEvaluations().getFirst().supportingEvidenceIds().size());
    }

    @Test
    void abandonmentWithoutReasonRequiresInputRatherThanMutatingOrGuessing() {
        Fixture fixture = seed(ConstraintMode.NON_BLOCKING);

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

    private Fixture seed(ConstraintMode mode) {
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
                Optional.of("sha256:m16"));
        Evidence supportingEvidence = new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("reviews/auditability.txt"),
                Optional.empty(),
                Optional.of("sha256:auditability"));
        Provenance provenance = new Provenance(
                new ProviderId("m16-fixture"),
                Optional.of("1"),
                SourceLocator.file("openspec/change.md"),
                Optional.of("m16"),
                Optional.of("revision-m16"),
                evidence.id());
        Specification specification = new Specification(
                specificationId, projectId, "m16", "M16", Optional.empty(), provenance);
        Requirement requirement = new Requirement(
                requirementId,
                specificationId,
                Optional.of("REQ-M16"),
                "M16 orchestration",
                "JARVIS consumes explicit MORPHEUS constraint policy decisions",
                provenance);
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(),
                projectId,
                Optional.of("m16-orchestration"),
                "Expose constraint policy",
                "Expose constraint facts without inventing blocking semantics",
                List.of("Read-only surface"),
                List.of(),
                List.of(),
                provenance);
        Constraint constraint = constraint(mode, change.id(), provenance, supportingEvidence.id());
        ImplementationTask completedTask = new ImplementationTask(
                TaskId.generate(), change.id(), Optional.of("TASK-M16-1"), "Implement API", true, provenance);

        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        MemoryTraceabilityStore traceability = new MemoryTraceabilityStore(core);
        MemoryExternalReferenceStore externalReferences = new MemoryExternalReferenceStore(core);

        core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-m16")));
        core.putSpecificationVersion(new SpecificationVersion(
                versionId,
                projectId,
                Optional.of(1L),
                Optional.of("provider-v1"),
                Optional.of("revision-m16"),
                T0,
                Optional.empty()));
        core.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("revision-m16"),
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
        List<Evidence> evidenceList = new ArrayList<>();
        evidenceList.add(evidence);
        if (!constraint.supportingEvidenceIds().isEmpty()) {
            evidenceList.add(supportingEvidence);
        }
        content.putSnapshotContent(new SnapshotBusinessContent(
                snapshotId,
                versionId,
                List.of(specification),
                List.of(),
                List.of(change),
                List.of(constraint),
                List.of(),
                List.of(completedTask),
                evidenceList));
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

    private Constraint constraint(
            ConstraintMode mode,
            ChangeId changeId,
            Provenance provenance,
            EvidenceId supportingEvidenceId) {
        return switch (mode) {
            case UNKNOWN -> new Constraint(
                    ConstraintId.generate(), changeId, "Preserve auditability", provenance);
            case NON_BLOCKING -> new Constraint(
                    ConstraintId.generate(),
                    changeId,
                    "Preserve auditability",
                    ConstraintApplicability.APPLICABLE,
                    ConstraintSeverity.WARNING,
                    ConstraintSatisfaction.VIOLATED,
                    ConstraintBlockingPolicy.nonBlocking(),
                    List.of(supportingEvidenceId),
                    provenance);
            case BLOCKING_VERIFYING -> new Constraint(
                    ConstraintId.generate(),
                    changeId,
                    "Preserve auditability",
                    ConstraintApplicability.APPLICABLE,
                    ConstraintSeverity.CRITICAL,
                    ConstraintSatisfaction.VIOLATED,
                    ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.VERIFYING)),
                    List.of(supportingEvidenceId),
                    provenance);
        };
    }

    private enum ConstraintMode {
        UNKNOWN,
        NON_BLOCKING,
        BLOCKING_VERIFYING
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            ChangeProposal change,
            ImplementationTask completedTask,
            ChangeOrchestrationStateService stateService,
            ChangeTransitionEvaluationService transitionService) {
    }
}
