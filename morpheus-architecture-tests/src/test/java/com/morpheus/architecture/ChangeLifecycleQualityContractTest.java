package com.morpheus.architecture;

import com.morpheus.application.lifecycle.ChangeLifecycleBlocker;
import com.morpheus.application.lifecycle.ChangeLifecycleFacts;
import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.lifecycle.ChangeLifecycleStateMachine;
import com.morpheus.application.quality.ChangeCompletenessAssessment;
import com.morpheus.application.quality.ChangeCompletenessReport;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.ChangeLifecycleQualityAssessment;
import com.morpheus.application.quality.ChangeLifecycleQualityService;
import com.morpheus.application.quality.LifecycleFactSource;
import com.morpheus.application.quality.QualityFactValue;
import com.morpheus.application.quality.QualityFindingCode;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
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
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeLifecycleQualityContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T20:20:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteProduceSameTriStateCompletenessWithoutInventingFacts() {
        Fixture fixture = Fixture.create();

        var memoryCore = new MemorySpecificationKnowledgeStore();
        var memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        var memoryTrace = new MemoryTraceabilityStore(memoryCore);
        seed(memoryCore, memoryCore, memoryContent, memoryTrace, fixture);
        ChangeCompletenessReport memory = completeness(memoryCore, memoryContent, memoryCore, memoryTrace)
                .assessActive(fixture.projectId())
                .orElseThrow();

        Path database = tempDir.resolve("change-completeness-parity.db");
        ChangeCompletenessReport sqlite;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            seed(snapshots, versions, content, traceability, fixture);
            sqlite = completeness(snapshots, content, versions, traceability)
                    .assessActive(fixture.projectId())
                    .orElseThrow();
        }

        assertEquals(memory, sqlite);

        ChangeCompletenessAssessment observable = find(memory, fixture.observableChange().id());
        assertEquals(1, observable.currentRequirementCount());
        assertEquals(1, observable.constraintCount());
        assertEquals(1, observable.designDecisionCount());
        assertEquals(1, observable.implementationTaskCount());
        assertEquals(QualityFactValue.TRUE, observable.lifecycleFacts().requirementsIdentified());
        assertEquals(QualityFactValue.TRUE, observable.lifecycleFacts().designDecisionsAvailable());
        assertEquals(QualityFactValue.TRUE, observable.lifecycleFacts().planPresent());
        assertEquals(QualityFactValue.UNAVAILABLE, observable.lifecycleFacts().criticalConstraintsKnown());
        assertEquals(QualityFactValue.UNAVAILABLE, observable.lifecycleFacts().acceptanceCriteriaDefined());
        assertEquals(QualityFactValue.UNAVAILABLE, observable.lifecycleFacts().designRequired());
        assertEquals(QualityFactValue.UNAVAILABLE, observable.lifecycleFacts().knownBlocker());
        assertFalse(fixture.observableChange().risks().isEmpty());
        assertTrue(fixture.completedTask().completed());
        assertEquals(List.of(QualityFindingCode.CHANGE_COMPLETENESS_PARTIALLY_OBSERVABLE),
                observable.findings().stream().map(finding -> finding.code()).toList());

        ChangeCompletenessAssessment partial = find(memory, fixture.partialChange().id());
        assertEquals(0, partial.currentRequirementCount());
        assertEquals(QualityFactValue.FALSE, partial.lifecycleFacts().requirementsIdentified());
        assertEquals(QualityFactValue.FALSE, partial.lifecycleFacts().designDecisionsAvailable());
        assertEquals(QualityFactValue.UNAVAILABLE, partial.lifecycleFacts().planPresent());
        assertTrue(partial.findings().stream().anyMatch(finding ->
                finding.code() == QualityFindingCode.CHANGE_WITHOUT_CURRENT_REQUIREMENT));
    }

    @Test
    void derivedProposedToSpecifiedRemainsUnevaluatedWhenRequiredFactsAreUnavailable() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var traceability = new MemoryTraceabilityStore(core);
        seed(core, core, content, traceability, fixture);
        ChangeLifecycleQualityService service = lifecycle(core, content, core, traceability);

        ChangeLifecycleQualityAssessment result = service.assessDerivedActive(
                        fixture.projectId(),
                        ChangeLifecycle.of(fixture.observableChange().id(), ChangeLifecycleState.PROPOSED),
                        ChangeLifecycleState.SPECIFIED,
                        ChangeLifecyclePolicy.forwardOnly(),
                        Optional.empty())
                .orElseThrow();

        assertEquals(LifecycleFactSource.DERIVED, result.factSource());
        assertEquals(List.of(
                "requirementsIdentified",
                "criticalConstraintsKnown",
                "acceptanceCriteriaDefined"), result.requiredFacts());
        assertEquals(List.of(
                "criticalConstraintsKnown",
                "acceptanceCriteriaDefined"), result.unavailableRequiredFacts());
        assertTrue(result.decision().isEmpty());
        assertEquals(2, result.findings().size());
        assertTrue(result.findings().stream().allMatch(finding ->
                finding.code() == QualityFindingCode.LIFECYCLE_REQUIRED_FACT_UNAVAILABLE));
    }

    @Test
    void derivedTransitionWithoutRequiredFactsDelegatesToM3AndNeverInfersLifecycleFromCompletedTask() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var traceability = new MemoryTraceabilityStore(core);
        seed(core, core, content, traceability, fixture);
        ChangeLifecycleQualityService service = lifecycle(core, content, core, traceability);

        assertTrue(fixture.completedTask().completed());
        ChangeLifecycle source = ChangeLifecycle.of(fixture.observableChange().id(), ChangeLifecycleState.DRAFT);
        ChangeLifecycleQualityAssessment result = service.assessDerivedActive(
                        fixture.projectId(), source, ChangeLifecycleState.PROPOSED,
                        ChangeLifecyclePolicy.forwardOnly(), Optional.empty())
                .orElseThrow();

        assertTrue(result.requiredFacts().isEmpty());
        assertTrue(result.unavailableRequiredFacts().isEmpty());
        assertTrue(result.decision().orElseThrow().allowed());
        assertEquals(ChangeLifecycleState.PROPOSED,
                result.decision().orElseThrow().target().orElseThrow().state());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void explicitFactsPreserveExactM3Blockers() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var traceability = new MemoryTraceabilityStore(core);
        seed(core, core, content, traceability, fixture);
        ChangeLifecycleQualityService service = lifecycle(core, content, core, traceability);

        ChangeLifecycleFacts explicit = new ChangeLifecycleFacts(
                false, false, false,
                false, false, false,
                false, false, false);
        ChangeLifecycleQualityAssessment result = service.assessExplicitActive(
                        fixture.projectId(),
                        ChangeLifecycle.of(fixture.observableChange().id(), ChangeLifecycleState.PROPOSED),
                        ChangeLifecycleState.SPECIFIED,
                        explicit,
                        ChangeLifecyclePolicy.forwardOnly(),
                        Optional.empty())
                .orElseThrow();

        assertEquals(LifecycleFactSource.EXPLICIT, result.factSource());
        assertTrue(result.unavailableRequiredFacts().isEmpty());
        assertEquals(List.of(
                ChangeLifecycleBlocker.MISSING_REQUIREMENTS,
                ChangeLifecycleBlocker.UNKNOWN_CRITICAL_CONSTRAINTS,
                ChangeLifecycleBlocker.MISSING_ACCEPTANCE_CRITERIA),
                result.decision().orElseThrow().blockers());
        assertEquals(3, result.findings().size());
        assertTrue(result.findings().stream().allMatch(finding ->
                finding.code() == QualityFindingCode.LIFECYCLE_TRANSITION_BLOCKED));
    }

    @Test
    void noTaskMeansPlanUnavailableNotMissingPlanAndUnknownChangeIsRejected() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var traceability = new MemoryTraceabilityStore(core);
        seed(core, core, content, traceability, fixture);
        ChangeLifecycleQualityService service = lifecycle(core, content, core, traceability);

        ChangeLifecycleQualityAssessment result = service.assessDerivedActive(
                        fixture.projectId(),
                        ChangeLifecycle.of(fixture.partialChange().id(), ChangeLifecycleState.DESIGNED),
                        ChangeLifecycleState.PLANNED,
                        ChangeLifecyclePolicy.forwardOnly(),
                        Optional.empty())
                .orElseThrow();

        assertEquals(QualityFactValue.UNAVAILABLE, result.facts().planPresent());
        assertEquals(List.of("planPresent"), result.unavailableRequiredFacts());
        assertTrue(result.decision().isEmpty());
        assertEquals(QualityFindingCode.LIFECYCLE_REQUIRED_FACT_UNAVAILABLE, result.findings().get(0).code());
        assertFalse(result.findings().stream().anyMatch(finding ->
                "MISSING_PLAN".equals(finding.details().get("blocker"))));

        assertThrows(KnowledgeStoreException.class, () -> service.assessDerivedActive(
                fixture.projectId(),
                ChangeLifecycle.of(ChangeId.generate(), ChangeLifecycleState.DRAFT),
                ChangeLifecycleState.PROPOSED,
                ChangeLifecyclePolicy.forwardOnly(),
                Optional.empty()).orElseThrow());
    }

    @Test
    void qualityUsesActiveAllowsRetiredRejectsReadyAndKeepsMissingActiveDistinct() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var traceability = new MemoryTraceabilityStore(core);
        seed(core, core, content, traceability, fixture);
        ChangeCompletenessService completeness = completeness(core, content, core, traceability);
        ChangeLifecycleQualityService lifecycle = lifecycle(core, content, core, traceability);

        assertEquals(fixture.activeSnapshotId(),
                completeness.assessActive(fixture.projectId()).orElseThrow().snapshot().id());
        assertEquals(KnowledgeSnapshotState.RETIRED,
                completeness.assessSnapshot(fixture.retiredSnapshotId()).snapshot().state());
        assertThrows(KnowledgeStoreException.class,
                () -> completeness.assessSnapshot(fixture.readySnapshotId()));
        assertThrows(KnowledgeStoreException.class, () -> lifecycle.assessDerivedSnapshot(
                fixture.readySnapshotId(),
                ChangeLifecycle.of(fixture.readyChange().id(), ChangeLifecycleState.DRAFT),
                ChangeLifecycleState.PROPOSED,
                ChangeLifecyclePolicy.forwardOnly(),
                Optional.empty()));

        var emptyCore = new MemorySpecificationKnowledgeStore();
        var emptyContent = new MemorySnapshotBusinessContentStore(emptyCore, emptyCore);
        var emptyTrace = new MemoryTraceabilityStore(emptyCore);
        assertTrue(completeness(emptyCore, emptyContent, emptyCore, emptyTrace)
                .assessActive(ProjectSpecificationId.generate()).isEmpty());
    }

    @Test
    void sqliteReopenPreservesCompletenessAndLifecycleAssessments() {
        Fixture fixture = Fixture.create();
        Path database = tempDir.resolve("change-lifecycle-quality-reopen.db");
        ChangeCompletenessReport beforeCompleteness;
        ChangeLifecycleQualityAssessment beforeLifecycle;

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            seed(snapshots, versions, content, traceability, fixture);
            beforeCompleteness = completeness(snapshots, content, versions, traceability)
                    .assessActive(fixture.projectId()).orElseThrow();
            beforeLifecycle = lifecycle(snapshots, content, versions, traceability)
                    .assessDerivedActive(
                            fixture.projectId(),
                            ChangeLifecycle.of(fixture.observableChange().id(), ChangeLifecycleState.DRAFT),
                            ChangeLifecycleState.PROPOSED,
                            ChangeLifecyclePolicy.forwardOnly(),
                            Optional.empty())
                    .orElseThrow();
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            assertEquals(beforeCompleteness,
                    completeness(snapshots, content, versions, traceability)
                            .assessActive(fixture.projectId()).orElseThrow());
            assertEquals(beforeLifecycle,
                    lifecycle(snapshots, content, versions, traceability)
                            .assessDerivedActive(
                                    fixture.projectId(),
                                    ChangeLifecycle.of(fixture.observableChange().id(), ChangeLifecycleState.DRAFT),
                                    ChangeLifecycleState.PROPOSED,
                                    ChangeLifecyclePolicy.forwardOnly(),
                                    Optional.empty())
                            .orElseThrow());
        }
    }

    private ChangeCompletenessService completeness(
            SpecificationKnowledgeStore snapshots,
            SnapshotBusinessContentStore content,
            VersionedRequirementStore requirements,
            TraceabilityStore traceability) {
        return new ChangeCompletenessService(snapshots, content, requirements, traceability);
    }

    private ChangeLifecycleQualityService lifecycle(
            SpecificationKnowledgeStore snapshots,
            SnapshotBusinessContentStore content,
            VersionedRequirementStore requirements,
            TraceabilityStore traceability) {
        return new ChangeLifecycleQualityService(
                snapshots,
                completeness(snapshots, content, requirements, traceability),
                new ChangeLifecycleStateMachine());
    }

    private ChangeCompletenessAssessment find(ChangeCompletenessReport report, ChangeId changeId) {
        return report.changes().stream()
                .filter(item -> item.change().id().equals(changeId))
                .findFirst()
                .orElseThrow();
    }

    private void seed(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            SnapshotBusinessContentStore content,
            TraceabilityStore traceability,
            Fixture fixture) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace-m6-s3")));

        versions.putSpecificationVersion(specificationVersion(
                fixture.retiredVersionId(), fixture.projectId(), 1L, Optional.empty()));
        snapshots.putSnapshot(snapshot(
                fixture.retiredSnapshotId(), fixture.projectId(), Optional.empty(),
                KnowledgeSnapshotState.READY, "revision-1", T0));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.retiredSnapshotId(), fixture.retiredVersionId()));
        versions.putRequirementVersion(fixture.retiredRequirement());
        content.putSnapshotContent(fixture.retiredContent());
        traceability.putLink(fixture.retiredSnapshotId(), fixture.retiredAffects());
        snapshots.activateSnapshot(fixture.retiredSnapshotId(), Optional.empty());

        versions.putSpecificationVersion(specificationVersion(
                fixture.activeVersionId(), fixture.projectId(), 2L, Optional.of(fixture.retiredVersionId())));
        snapshots.putSnapshot(snapshot(
                fixture.activeSnapshotId(), fixture.projectId(), Optional.of(fixture.retiredSnapshotId()),
                KnowledgeSnapshotState.READY, "revision-2", T0.plusSeconds(10)));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.activeSnapshotId(), fixture.activeVersionId()));
        versions.putRequirementVersion(fixture.currentRequirement());
        versions.putRequirementVersion(fixture.proposedRequirement());
        content.putSnapshotContent(fixture.activeContent());
        fixture.activeLinks().forEach(link -> traceability.putLink(fixture.activeSnapshotId(), link));
        snapshots.activateSnapshot(fixture.activeSnapshotId(), Optional.of(fixture.retiredSnapshotId()));

        versions.putSpecificationVersion(specificationVersion(
                fixture.readyVersionId(), fixture.projectId(), 3L, Optional.of(fixture.activeVersionId())));
        snapshots.putSnapshot(snapshot(
                fixture.readySnapshotId(), fixture.projectId(), Optional.of(fixture.activeSnapshotId()),
                KnowledgeSnapshotState.READY, "revision-3", T0.plusSeconds(20)));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.readySnapshotId(), fixture.readyVersionId()));
        versions.putRequirementVersion(fixture.readyRequirement());
        content.putSnapshotContent(fixture.readyContent());
    }

    private SpecificationVersion specificationVersion(
            SpecificationVersionId id,
            ProjectSpecificationId projectId,
            long sequence,
            Optional<SpecificationVersionId> predecessor) {
        return new SpecificationVersion(
                id,
                projectId,
                Optional.of(sequence),
                Optional.of("provider-v1"),
                Optional.of("revision-" + sequence),
                T0.plusSeconds(sequence),
                predecessor);
    }

    private KnowledgeSnapshotMetadata snapshot(
            KnowledgeSnapshotId id,
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessor,
            KnowledgeSnapshotState state,
            String revision,
            Instant createdAt) {
        return new KnowledgeSnapshotMetadata(id, projectId, predecessor, state, Optional.of(revision), createdAt);
    }

    private static RequirementVersionRecord requirement(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            RequirementId requirementId,
            SpecificationId specificationId,
            TemporalState temporalState,
            Provenance provenance,
            String title) {
        Requirement content = new Requirement(
                requirementId,
                specificationId,
                Optional.of("REQ-" + title.toUpperCase().replace(' ', '-')),
                title,
                title + " statement",
                provenance);
        return new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        EntityVersionId.generate(),
                        requirementId.value(),
                        versionId,
                        temporalState,
                        content));
    }

    private static TraceabilityLink affects(
            ChangeId changeId,
            RequirementId requirementId,
            EvidenceId evidenceId,
            Instant observedAt) {
        return new TraceabilityLink(
                TraceabilityLinkId.generate(),
                new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, changeId.value()),
                TraceabilityRelationType.AFFECTS,
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirementId.value()),
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(evidenceId),
                observedAt);
    }

    private static Provenance provenance(EvidenceId evidenceId, String path, String externalId) {
        return new Provenance(
                new ProviderId("m6-s3-fixture"),
                Optional.of("1"),
                SourceLocator.file(path),
                Optional.of(externalId),
                Optional.of("source-revision"),
                evidenceId);
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId retiredSnapshotId,
            KnowledgeSnapshotId activeSnapshotId,
            KnowledgeSnapshotId readySnapshotId,
            SpecificationVersionId retiredVersionId,
            SpecificationVersionId activeVersionId,
            SpecificationVersionId readyVersionId,
            RequirementVersionRecord retiredRequirement,
            RequirementVersionRecord currentRequirement,
            RequirementVersionRecord proposedRequirement,
            RequirementVersionRecord readyRequirement,
            SnapshotBusinessContent retiredContent,
            SnapshotBusinessContent activeContent,
            SnapshotBusinessContent readyContent,
            TraceabilityLink retiredAffects,
            List<TraceabilityLink> activeLinks,
            ChangeProposal observableChange,
            ChangeProposal partialChange,
            ChangeProposal readyChange,
            ImplementationTask completedTask) {

        static Fixture create() {
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            KnowledgeSnapshotId retiredSnapshotId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId activeSnapshotId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId readySnapshotId = KnowledgeSnapshotId.generate();
            SpecificationVersionId retiredVersionId = SpecificationVersionId.generate();
            SpecificationVersionId activeVersionId = SpecificationVersionId.generate();
            SpecificationVersionId readyVersionId = SpecificationVersionId.generate();

            Evidence retiredEvidence = evidence("retired");
            Evidence activeEvidence = evidence("active");
            Evidence readyEvidence = evidence("ready");
            Provenance retiredProvenance = provenance(retiredEvidence.id(), "specs/retired.md", "RETIRED");
            Provenance activeProvenance = provenance(activeEvidence.id(), "specs/active.md", "ACTIVE");
            Provenance readyProvenance = provenance(readyEvidence.id(), "specs/ready.md", "READY");

            SpecificationId retiredSpecificationId = SpecificationId.generate();
            Specification retiredSpecification = new Specification(
                    retiredSpecificationId, projectId, "retired", "Retired", Optional.empty(), retiredProvenance);
            RequirementId retiredRequirementId = RequirementId.generate();
            RequirementVersionRecord retiredRequirement = requirement(
                    retiredSnapshotId, retiredVersionId, retiredRequirementId, retiredSpecificationId,
                    TemporalState.CURRENT, retiredProvenance, "retired");
            ChangeProposal retiredChange = change(projectId, "Retired change", List.of(), retiredProvenance);
            SnapshotBusinessContent retiredContent = businessContent(
                    retiredSnapshotId, retiredVersionId, retiredSpecification,
                    List.of(retiredChange), List.of(), List.of(), List.of(), retiredEvidence);
            TraceabilityLink retiredAffects = affects(
                    retiredChange.id(), retiredRequirementId, retiredEvidence.id(), T0);

            SpecificationId activeSpecificationId = SpecificationId.generate();
            Specification activeSpecification = new Specification(
                    activeSpecificationId, projectId, "active", "Active", Optional.empty(), activeProvenance);
            RequirementId currentRequirementId = RequirementId.generate();
            RequirementVersionRecord currentRequirement = requirement(
                    activeSnapshotId, activeVersionId, currentRequirementId, activeSpecificationId,
                    TemporalState.CURRENT, activeProvenance, "current");
            RequirementId proposedRequirementId = RequirementId.generate();
            RequirementVersionRecord proposedRequirement = requirement(
                    activeSnapshotId, activeVersionId, proposedRequirementId, activeSpecificationId,
                    TemporalState.PROPOSED, activeProvenance, "proposed");

            ChangeProposal observableChange = change(
                    projectId, "Observable change", List.of("known migration risk"), activeProvenance);
            ChangeProposal partialChange = change(
                    projectId, "Partial change", List.of(), activeProvenance);
            Constraint constraint = new Constraint(
                    ConstraintId.generate(), observableChange.id(), "Preserve auditability", activeProvenance);
            DesignDecision decision = new DesignDecision(
                    DesignDecisionId.generate(), observableChange.id(),
                    "Use explicit state", "Avoid implicit lifecycle inference", activeProvenance);
            ImplementationTask completedTask = new ImplementationTask(
                    TaskId.generate(), observableChange.id(), Optional.of("TASK-DONE"),
                    "Implementation already marked done", true, activeProvenance);
            SnapshotBusinessContent activeContent = businessContent(
                    activeSnapshotId,
                    activeVersionId,
                    activeSpecification,
                    List.of(observableChange, partialChange),
                    List.of(constraint),
                    List.of(decision),
                    List.of(completedTask),
                    activeEvidence);
            List<TraceabilityLink> activeLinks = List.of(
                    affects(observableChange.id(), currentRequirementId, activeEvidence.id(), T0.plusSeconds(10)),
                    affects(partialChange.id(), proposedRequirementId, activeEvidence.id(), T0.plusSeconds(11)));

            SpecificationId readySpecificationId = SpecificationId.generate();
            Specification readySpecification = new Specification(
                    readySpecificationId, projectId, "ready", "Ready", Optional.empty(), readyProvenance);
            RequirementId readyRequirementId = RequirementId.generate();
            RequirementVersionRecord readyRequirement = requirement(
                    readySnapshotId, readyVersionId, readyRequirementId, readySpecificationId,
                    TemporalState.CURRENT, readyProvenance, "ready");
            ChangeProposal readyChange = change(projectId, "Ready change", List.of(), readyProvenance);
            SnapshotBusinessContent readyContent = businessContent(
                    readySnapshotId, readyVersionId, readySpecification,
                    List.of(readyChange), List.of(), List.of(), List.of(), readyEvidence);

            return new Fixture(
                    projectId,
                    retiredSnapshotId,
                    activeSnapshotId,
                    readySnapshotId,
                    retiredVersionId,
                    activeVersionId,
                    readyVersionId,
                    retiredRequirement,
                    currentRequirement,
                    proposedRequirement,
                    readyRequirement,
                    retiredContent,
                    activeContent,
                    readyContent,
                    retiredAffects,
                    activeLinks,
                    observableChange,
                    partialChange,
                    readyChange,
                    completedTask);
        }

        private static Evidence evidence(String name) {
            return new Evidence(
                    EvidenceId.generate(),
                    SourceLocator.file("specs/" + name + ".md"),
                    Optional.empty(),
                    Optional.of("sha256:" + name));
        }

        private static ChangeProposal change(
                ProjectSpecificationId projectId,
                String title,
                List<String> risks,
                Provenance provenance) {
            return new ChangeProposal(
                    ChangeId.generate(),
                    projectId,
                    Optional.empty(),
                    title,
                    title + " intent",
                    List.of(),
                    List.of(),
                    risks,
                    provenance);
        }

        private static SnapshotBusinessContent businessContent(
                KnowledgeSnapshotId snapshotId,
                SpecificationVersionId versionId,
                Specification specification,
                List<ChangeProposal> changes,
                List<Constraint> constraints,
                List<DesignDecision> decisions,
                List<ImplementationTask> tasks,
                Evidence evidence) {
            return new SnapshotBusinessContent(
                    snapshotId,
                    versionId,
                    List.of(specification),
                    List.of(),
                    changes,
                    constraints,
                    decisions,
                    tasks,
                    List.of(evidence));
        }
    }
}
