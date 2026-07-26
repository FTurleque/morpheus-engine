package com.morpheus.architecture;

import com.morpheus.application.quality.AcceptanceCoverageAssessment;
import com.morpheus.application.quality.AcceptanceCoverageStatus;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.QualityEvidenceKind;
import com.morpheus.application.quality.QualityFindingCode;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.quality.TaskRequirementCoverage;
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
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
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

class TaskAcceptanceQualityContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T19:05:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteProduceSameTaskCoverageAndOnlyCurrentRequirementTargetsCover() {
        Fixture fixture = Fixture.create();

        var memoryCore = new MemorySpecificationKnowledgeStore();
        var memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        var memoryTrace = new MemoryTraceabilityStore(memoryCore);
        seed(memoryCore, memoryCore, memoryContent, memoryTrace, fixture, fixture.activeContent());
        TaskRequirementCoverage memory = taskService(memoryCore, memoryContent, memoryCore, memoryTrace)
                .assessActive(fixture.projectId())
                .orElseThrow();

        Path database = tempDir.resolve("task-quality-parity.db");
        TaskRequirementCoverage sqlite;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            seed(snapshots, versions, content, traceability, fixture, fixture.activeContent());
            sqlite = taskService(snapshots, content, versions, traceability)
                    .assessActive(fixture.projectId())
                    .orElseThrow();
        }

        assertEquals(memory, sqlite);
        assertEquals(4, memory.totalTasks());
        assertEquals(1, memory.coveredTasks());
        assertEquals(3, memory.uncoveredTasks());
        assertEquals(0.25, memory.coverageRatio());
        assertEquals(3, memory.findings().size());
        assertTrue(memory.findings().stream().allMatch(finding ->
                finding.code() == QualityFindingCode.IMPLEMENTATION_TASK_WITHOUT_REQUIREMENT
                        && finding.severity() == DiagnosticSeverity.WARNING
                        && finding.evidenceKind() == QualityEvidenceKind.DETERMINISTIC
                        && finding.confidence().isEmpty()
                        && finding.subject().kind() == TraceabilityEntityKind.IMPLEMENTATION_TASK));
        assertFalse(memory.findings().stream().anyMatch(finding ->
                finding.subject().identity().equals(fixture.coveredTask().id().value())));
    }

    @Test
    void proposedOnlyAndMissingTargetsDoNotCountAsTaskCoverage() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var traceability = new MemoryTraceabilityStore(core);
        seed(core, core, content, traceability, fixture, fixture.activeContent());

        TaskRequirementCoverage result = taskService(core, content, core, traceability)
                .assessActive(fixture.projectId())
                .orElseThrow();

        Set<String> uncoveredTaskIds = result.findings().stream()
                .map(finding -> finding.details().get("taskId"))
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(uncoveredTaskIds.contains(fixture.proposedOnlyTask().id().toString()));
        assertTrue(uncoveredTaskIds.contains(fixture.missingTargetTask().id().toString()));
        assertTrue(uncoveredTaskIds.contains(fixture.noRelationTask().id().toString()));
        assertFalse(uncoveredTaskIds.contains(fixture.coveredTask().id().toString()));
    }

    @Test
    void taskQualityUsesActiveAllowsRetiredAndRejectsReadyOrUnknownSnapshots() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var traceability = new MemoryTraceabilityStore(core);
        seed(core, core, content, traceability, fixture, fixture.activeContent());
        TaskQualityService service = taskService(core, content, core, traceability);

        assertEquals(fixture.activeSnapshotId(), service.assessActive(fixture.projectId()).orElseThrow().snapshot().id());
        assertEquals(KnowledgeSnapshotState.RETIRED, service.assessSnapshot(fixture.retiredSnapshotId()).snapshot().state());
        assertThrows(KnowledgeStoreException.class, () -> service.assessSnapshot(fixture.readySnapshotId()));
        assertThrows(KnowledgeStoreException.class, () -> service.assessSnapshot(KnowledgeSnapshotId.generate()));
    }

    @Test
    void zeroTasksAreFullyCoveredWithoutSyntheticFindings() {
        Fixture fixture = Fixture.create();
        SnapshotBusinessContent withoutTasks = new SnapshotBusinessContent(
                fixture.activeContent().snapshotId(),
                fixture.activeContent().specificationVersionId(),
                fixture.activeContent().specifications(),
                fixture.activeContent().scenarios(),
                fixture.activeContent().changes(),
                fixture.activeContent().constraints(),
                fixture.activeContent().designDecisions(),
                List.of(),
                fixture.activeContent().acceptanceCriteria(),
                fixture.activeContent().evidence());

        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var traceability = new MemoryTraceabilityStore(core);
        seed(core, core, content, traceability, fixture, withoutTasks);

        TaskRequirementCoverage result = taskService(core, content, core, traceability)
                .assessActive(fixture.projectId())
                .orElseThrow();
        assertEquals(0, result.totalTasks());
        assertEquals(0, result.uncoveredTasks());
        assertEquals(1.0, result.coverageRatio());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void sqliteReopenPreservesTaskQualityAssessment() {
        Fixture fixture = Fixture.create();
        Path database = tempDir.resolve("task-quality-reopen.db");
        TaskRequirementCoverage before;

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            seed(snapshots, versions, content, traceability, fixture, fixture.activeContent());
            before = taskService(snapshots, content, versions, traceability)
                    .assessActive(fixture.projectId())
                    .orElseThrow();
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            TaskRequirementCoverage after = taskService(snapshots, content, versions, traceability)
                    .assessActive(fixture.projectId())
                    .orElseThrow();
            assertEquals(before, after);
        }
    }

    @Test
    void acceptanceCoverageIsAvailableAndNeverConvertsScenarios() throws ClassNotFoundException {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var traceability = new MemoryTraceabilityStore(core);
        seed(core, core, content, traceability, fixture, fixture.activeContent());

        AcceptanceCoverageAssessment assessment = new AcceptanceQualityService(core, content)
                .assessActive(fixture.projectId())
                .orElseThrow();

        assertEquals(AcceptanceCoverageStatus.NO_CRITERIA, assessment.status());
        assertEquals(0, assessment.totalCriteria());
        assertEquals(1.0, assessment.verifiedCoverageRatio());
        assertTrue(assessment.findings().isEmpty());
        assertEquals(1, fixture.activeContent().scenarios().size());
        assertTrue(fixture.activeContent().acceptanceCriteria().isEmpty());
        assertEquals(ProviderCapability.READ_ACCEPTANCE_CRITERIA, ProviderCapability.valueOf("READ_ACCEPTANCE_CRITERIA"));
        assertEquals(
                "com.morpheus.domain.acceptance.AcceptanceCriterion",
                Class.forName("com.morpheus.domain.acceptance.AcceptanceCriterion").getName());
    }

    @Test
    void acceptanceAssessmentHasBackendParityAndPublishedSnapshotPolicy() {
        Fixture fixture = Fixture.create();

        var memoryCore = new MemorySpecificationKnowledgeStore();
        var memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        var memoryTrace = new MemoryTraceabilityStore(memoryCore);
        seed(memoryCore, memoryCore, memoryContent, memoryTrace, fixture, fixture.activeContent());
        AcceptanceQualityService memoryService = new AcceptanceQualityService(memoryCore, memoryContent);
        AcceptanceCoverageAssessment memory = memoryService.assessActive(fixture.projectId()).orElseThrow();
        assertEquals(KnowledgeSnapshotState.RETIRED,
                memoryService.assessSnapshot(fixture.retiredSnapshotId()).snapshot().state());
        assertThrows(KnowledgeStoreException.class, () -> memoryService.assessSnapshot(fixture.readySnapshotId()));

        Path database = tempDir.resolve("acceptance-quality-parity.db");
        AcceptanceCoverageAssessment sqlite;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            seed(snapshots, versions, content, traceability, fixture, fixture.activeContent());
            sqlite = new AcceptanceQualityService(snapshots, content)
                    .assessActive(fixture.projectId())
                    .orElseThrow();
        }
        assertEquals(memory, sqlite);
        assertEquals(AcceptanceCoverageStatus.NO_CRITERIA, sqlite.status());
    }

    private TaskQualityService taskService(
            SpecificationKnowledgeStore snapshots,
            SnapshotBusinessContentStore content,
            VersionedRequirementStore requirements,
            TraceabilityStore traceability) {
        return new TaskQualityService(snapshots, content, requirements, traceability);
    }

    private void seed(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            SnapshotBusinessContentStore content,
            TraceabilityStore traceability,
            Fixture fixture,
            SnapshotBusinessContent activeContent) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace-m6")));

        versions.putSpecificationVersion(specificationVersion(
                fixture.retiredVersionId(), fixture.projectId(), 1L, Optional.empty()));
        snapshots.putSnapshot(snapshot(
                fixture.retiredSnapshotId(), fixture.projectId(), Optional.empty(), KnowledgeSnapshotState.READY, "revision-1", T0));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.retiredSnapshotId(), fixture.retiredVersionId()));
        versions.putRequirementVersion(fixture.retiredRequirement());
        content.putSnapshotContent(fixture.retiredContent());
        traceability.putLink(fixture.retiredSnapshotId(), fixture.retiredAffects());
        snapshots.activateSnapshot(fixture.retiredSnapshotId(), Optional.empty());

        versions.putSpecificationVersion(specificationVersion(
                fixture.activeVersionId(), fixture.projectId(), 2L, Optional.of(fixture.retiredVersionId())));
        snapshots.putSnapshot(snapshot(
                fixture.activeSnapshotId(), fixture.projectId(), Optional.of(fixture.retiredSnapshotId()), KnowledgeSnapshotState.READY, "revision-2", T0.plusSeconds(10)));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.activeSnapshotId(), fixture.activeVersionId()));
        versions.putRequirementVersion(fixture.currentRequirement());
        versions.putRequirementVersion(fixture.proposedOnlyRequirement());
        content.putSnapshotContent(activeContent);
        fixture.activeLinks().forEach(link -> traceability.putLink(fixture.activeSnapshotId(), link));
        snapshots.activateSnapshot(fixture.activeSnapshotId(), Optional.of(fixture.retiredSnapshotId()));

        versions.putSpecificationVersion(specificationVersion(
                fixture.readyVersionId(), fixture.projectId(), 3L, Optional.of(fixture.activeVersionId())));
        snapshots.putSnapshot(snapshot(
                fixture.readySnapshotId(), fixture.projectId(), Optional.of(fixture.activeSnapshotId()), KnowledgeSnapshotState.READY, "revision-3", T0.plusSeconds(20)));
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
            TraceabilityResolutionState resolution,
            EvidenceId evidenceId,
            Instant observedAt) {
        return new TraceabilityLink(
                TraceabilityLinkId.generate(),
                new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, changeId.value()),
                TraceabilityRelationType.AFFECTS,
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirementId.value()),
                TraceabilityLinkOrigin.DERIVED,
                resolution,
                Optional.empty(),
                Set.of(evidenceId),
                observedAt);
    }

    private static Provenance provenance(EvidenceId evidenceId, String path, String externalId) {
        return new Provenance(
                new ProviderId("m6-quality-fixture"),
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
            RequirementVersionRecord proposedOnlyRequirement,
            RequirementVersionRecord readyRequirement,
            SnapshotBusinessContent retiredContent,
            SnapshotBusinessContent activeContent,
            SnapshotBusinessContent readyContent,
            TraceabilityLink retiredAffects,
            List<TraceabilityLink> activeLinks,
            ImplementationTask coveredTask,
            ImplementationTask proposedOnlyTask,
            ImplementationTask missingTargetTask,
            ImplementationTask noRelationTask) {

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
                    retiredSpecificationId, projectId, "retired", "Retired specification", Optional.empty(), retiredProvenance);
            RequirementId retiredRequirementId = RequirementId.generate();
            RequirementVersionRecord retiredRequirement = requirement(
                    retiredSnapshotId, retiredVersionId, retiredRequirementId, retiredSpecificationId,
                    TemporalState.CURRENT, retiredProvenance, "retired requirement");
            ChangeProposal retiredChange = change(projectId, "retired change", retiredProvenance);
            ImplementationTask retiredTask = task(retiredChange.id(), "retired task", retiredProvenance);
            SnapshotBusinessContent retiredContent = businessContent(
                    retiredSnapshotId, retiredVersionId, retiredSpecification, List.of(), List.of(retiredChange), List.of(retiredTask), retiredEvidence);
            TraceabilityLink retiredAffects = affects(
                    retiredChange.id(), retiredRequirementId, TraceabilityResolutionState.RESOLVED,
                    retiredEvidence.id(), T0);

            SpecificationId activeSpecificationId = SpecificationId.generate();
            Specification activeSpecification = new Specification(
                    activeSpecificationId, projectId, "active", "Active specification", Optional.empty(), activeProvenance);
            RequirementId currentRequirementId = RequirementId.generate();
            RequirementVersionRecord currentRequirement = requirement(
                    activeSnapshotId, activeVersionId, currentRequirementId, activeSpecificationId,
                    TemporalState.CURRENT, activeProvenance, "current requirement");
            RequirementId proposedOnlyRequirementId = RequirementId.generate();
            RequirementVersionRecord proposedOnlyRequirement = requirement(
                    activeSnapshotId, activeVersionId, proposedOnlyRequirementId, activeSpecificationId,
                    TemporalState.PROPOSED, activeProvenance, "proposed only requirement");

            ChangeProposal coveredChange = change(projectId, "covered change", activeProvenance);
            ChangeProposal proposedOnlyChange = change(projectId, "proposed target change", activeProvenance);
            ChangeProposal missingTargetChange = change(projectId, "missing target change", activeProvenance);
            ChangeProposal noRelationChange = change(projectId, "no relation change", activeProvenance);
            ImplementationTask coveredTask = task(coveredChange.id(), "covered task", activeProvenance);
            ImplementationTask proposedOnlyTask = task(proposedOnlyChange.id(), "proposed only task", activeProvenance);
            ImplementationTask missingTargetTask = task(missingTargetChange.id(), "missing target task", activeProvenance);
            ImplementationTask noRelationTask = task(noRelationChange.id(), "no relation task", activeProvenance);
            Scenario scenario = new Scenario(
                    ScenarioId.generate(),
                    Optional.of(currentRequirementId),
                    "Current scenario",
                    List.of("project is active"),
                    "actor performs action",
                    "requirement remains satisfied",
                    activeProvenance);
            SnapshotBusinessContent activeContent = businessContent(
                    activeSnapshotId,
                    activeVersionId,
                    activeSpecification,
                    List.of(scenario),
                    List.of(coveredChange, proposedOnlyChange, missingTargetChange, noRelationChange),
                    List.of(coveredTask, proposedOnlyTask, missingTargetTask, noRelationTask),
                    activeEvidence);
            RequirementId missingRequirementId = RequirementId.generate();
            List<TraceabilityLink> activeLinks = List.of(
                    affects(coveredChange.id(), currentRequirementId, TraceabilityResolutionState.RESOLVED,
                            activeEvidence.id(), T0.plusSeconds(10)),
                    affects(proposedOnlyChange.id(), proposedOnlyRequirementId, TraceabilityResolutionState.RESOLVED,
                            activeEvidence.id(), T0.plusSeconds(11)),
                    affects(missingTargetChange.id(), missingRequirementId, TraceabilityResolutionState.UNRESOLVED,
                            activeEvidence.id(), T0.plusSeconds(12)));

            SpecificationId readySpecificationId = SpecificationId.generate();
            Specification readySpecification = new Specification(
                    readySpecificationId, projectId, "ready", "Ready specification", Optional.empty(), readyProvenance);
            RequirementId readyRequirementId = RequirementId.generate();
            RequirementVersionRecord readyRequirement = requirement(
                    readySnapshotId, readyVersionId, readyRequirementId, readySpecificationId,
                    TemporalState.CURRENT, readyProvenance, "ready requirement");
            ChangeProposal readyChange = change(projectId, "ready change", readyProvenance);
            SnapshotBusinessContent readyContent = businessContent(
                    readySnapshotId, readyVersionId, readySpecification, List.of(), List.of(readyChange), List.of(), readyEvidence);

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
                    proposedOnlyRequirement,
                    readyRequirement,
                    retiredContent,
                    activeContent,
                    readyContent,
                    retiredAffects,
                    activeLinks,
                    coveredTask,
                    proposedOnlyTask,
                    missingTargetTask,
                    noRelationTask);
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
                Provenance provenance) {
            return new ChangeProposal(
                    ChangeId.generate(),
                    projectId,
                    Optional.empty(),
                    title,
                    title + " intent",
                    List.of(),
                    List.of(),
                    List.of(),
                    provenance);
        }

        private static ImplementationTask task(ChangeId changeId, String title, Provenance provenance) {
            return new ImplementationTask(
                    TaskId.generate(),
                    changeId,
                    Optional.empty(),
                    title,
                    false,
                    provenance);
        }

        private static SnapshotBusinessContent businessContent(
                KnowledgeSnapshotId snapshotId,
                SpecificationVersionId versionId,
                Specification specification,
                List<Scenario> scenarios,
                List<ChangeProposal> changes,
                List<ImplementationTask> tasks,
                Evidence evidence) {
            return new SnapshotBusinessContent(
                    snapshotId,
                    versionId,
                    List.of(specification),
                    scenarios,
                    changes,
                    List.of(),
                    List.of(),
                    tasks,
                    List.of(evidence));
        }
    }
}
