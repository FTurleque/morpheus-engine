package com.morpheus.architecture;

import com.morpheus.application.quality.AcceptanceCoverageStatus;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityFindingCode;
import com.morpheus.application.quality.QualityReport;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.store.ExternalReferenceStore;
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
import com.morpheus.store.memory.MemoryExternalReferenceStore;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
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

class AggregateQualityReportContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T20:40:00Z");

    @TempDir
    Path tempDir;

    @Test
    void aggregateReportHasBackendParityAndTreatsZeroAcceptanceAsAvailableEmptyModel() {
        Fixture fixture = Fixture.create();

        MemorySpecificationKnowledgeStore memoryCore = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        MemoryTraceabilityStore memoryTraceability = new MemoryTraceabilityStore(memoryCore);
        MemoryExternalReferenceStore memoryReferences = new MemoryExternalReferenceStore(memoryCore);
        seed(memoryCore, memoryCore, memoryContent, memoryTraceability, fixture);
        QualityReport memory = service(
                memoryCore, memoryContent, memoryCore, memoryTraceability, memoryReferences)
                .assessActive(fixture.projectId())
                .orElseThrow();

        Path database = tempDir.resolve("aggregate-quality-m15.db");
        QualityReport sqlite;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            seed(snapshots, versions, content, traceability, fixture);
            sqlite = service(snapshots, content, versions, traceability, references)
                    .assessActive(fixture.projectId())
                    .orElseThrow();
        }

        assertEquals(memory, sqlite);
        assertEquals(AcceptanceCoverageStatus.NO_CRITERIA, memory.acceptance().status());
        assertEquals(AcceptanceCoverageStatus.NO_CRITERIA, memory.metrics().acceptanceCoverageStatus());
        assertEquals(0, memory.acceptance().totalCriteria());
        assertEquals(1.0, memory.acceptance().verifiedCoverageRatio());
        assertTrue(memory.acceptance().findings().isEmpty());
        assertFalse(memory.findings().stream().anyMatch(finding ->
                finding.code() == QualityFindingCode.ACCEPTANCE_COVERAGE_UNAVAILABLE));
        assertEquals(1, memory.metrics().totalRequirements());
        assertEquals(1, memory.metrics().linkedRequirements());
        assertEquals(1, memory.metrics().totalTasks());
        assertEquals(1, memory.metrics().coveredTasks());
        assertEquals(1, memory.metrics().totalChanges());
        assertEquals(
                memory.findings().stream().sorted().toList(),
                memory.findings());
    }

    @Test
    void sqliteReopenPreservesAggregateM15QualityReport() {
        Fixture fixture = Fixture.create();
        Path database = tempDir.resolve("aggregate-quality-m15-reopen.db");
        QualityReport before;

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            seed(snapshots, versions, content, traceability, fixture);
            before = service(snapshots, content, versions, traceability, references)
                    .assessActive(fixture.projectId())
                    .orElseThrow();
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            QualityReport after = service(snapshots, content, versions, traceability, references)
                    .assessActive(fixture.projectId())
                    .orElseThrow();
            assertEquals(before, after);
            assertEquals(AcceptanceCoverageStatus.NO_CRITERIA, after.acceptance().status());
        }
    }

    @Test
    void aggregateReportUsesActiveSnapshotAndRejectsReadySnapshot() {
        Fixture fixture = Fixture.create();
        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        MemoryTraceabilityStore traceability = new MemoryTraceabilityStore(core);
        MemoryExternalReferenceStore references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, traceability, fixture);

        QualityReportService quality = service(core, content, core, traceability, references);
        assertEquals(fixture.activeSnapshotId(),
                quality.assessActive(fixture.projectId()).orElseThrow().snapshot().id());
        assertThrows(com.morpheus.application.store.KnowledgeStoreException.class,
                () -> quality.assessSnapshot(fixture.readySnapshotId()));
    }

    private QualityReportService service(
            SpecificationKnowledgeStore snapshots,
            SnapshotBusinessContentStore content,
            VersionedRequirementStore requirements,
            TraceabilityStore traceability,
            ExternalReferenceStore references) {
        return new QualityReportService(
                snapshots,
                new RequirementQualityService(snapshots, requirements, traceability),
                new TaskQualityService(snapshots, content, requirements, traceability),
                new AcceptanceQualityService(snapshots, content),
                new ChangeCompletenessService(snapshots, content, requirements, traceability),
                new DecisionReferenceQualityService(snapshots, content, requirements, traceability, references));
    }

    private void seed(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            SnapshotBusinessContentStore content,
            TraceabilityStore traceability,
            Fixture fixture) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace-m15-quality")));
        versions.putSpecificationVersion(new SpecificationVersion(
                fixture.activeVersionId(),
                fixture.projectId(),
                Optional.of(1L),
                Optional.of("provider-v1"),
                Optional.of("revision-1"),
                T0,
                Optional.empty()));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                fixture.activeSnapshotId(),
                fixture.projectId(),
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("revision-1"),
                T0));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.activeSnapshotId(), fixture.activeVersionId()));
        versions.putRequirementVersion(new RequirementVersionRecord(
                fixture.activeSnapshotId(),
                new EntityVersion<>(
                        EntityVersionId.generate(),
                        fixture.requirement().id().value(),
                        fixture.activeVersionId(),
                        TemporalState.CURRENT,
                        fixture.requirement())));
        content.putSnapshotContent(fixture.activeContent());
        fixture.links().forEach(link -> traceability.putLink(fixture.activeSnapshotId(), link));
        snapshots.activateSnapshot(fixture.activeSnapshotId(), Optional.empty());

        versions.putSpecificationVersion(new SpecificationVersion(
                fixture.readyVersionId(),
                fixture.projectId(),
                Optional.of(2L),
                Optional.of("provider-v1"),
                Optional.of("revision-2"),
                T0.plusSeconds(10),
                Optional.of(fixture.activeVersionId())));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                fixture.readySnapshotId(),
                fixture.projectId(),
                Optional.of(fixture.activeSnapshotId()),
                KnowledgeSnapshotState.READY,
                Optional.of("revision-2"),
                T0.plusSeconds(10)));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.readySnapshotId(), fixture.readyVersionId()));
        content.putSnapshotContent(new SnapshotBusinessContent(
                fixture.readySnapshotId(),
                fixture.readyVersionId(),
                fixture.activeContent().specifications(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                fixture.activeContent().evidence()));
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId activeSnapshotId,
            KnowledgeSnapshotId readySnapshotId,
            SpecificationVersionId activeVersionId,
            SpecificationVersionId readyVersionId,
            Requirement requirement,
            SnapshotBusinessContent activeContent,
            List<TraceabilityLink> links) {

        static Fixture create() {
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            KnowledgeSnapshotId activeSnapshotId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId readySnapshotId = KnowledgeSnapshotId.generate();
            SpecificationVersionId activeVersionId = SpecificationVersionId.generate();
            SpecificationVersionId readyVersionId = SpecificationVersionId.generate();
            Evidence evidence = new Evidence(
                    EvidenceId.generate(),
                    SourceLocator.file("specs/m15-quality.md"),
                    Optional.empty(),
                    Optional.of("sha256:m15-quality"));
            Provenance provenance = new Provenance(
                    new ProviderId("m15-quality"),
                    Optional.of("1"),
                    evidence.source(),
                    Optional.of("M15-QUALITY"),
                    Optional.of("revision-1"),
                    evidence.id());
            Specification specification = new Specification(
                    SpecificationId.generate(),
                    projectId,
                    "m15-quality",
                    "M15 quality",
                    Optional.empty(),
                    provenance);
            Requirement requirement = new Requirement(
                    RequirementId.generate(),
                    specification.id(),
                    Optional.of("REQ-M15-QUALITY"),
                    "Acceptance quality",
                    "MORPHEUS SHALL distinguish an empty acceptance model from an unavailable model.",
                    provenance);
            ChangeProposal change = new ChangeProposal(
                    ChangeId.generate(),
                    projectId,
                    Optional.of("m15-quality"),
                    "M15 quality change",
                    "Exercise aggregate quality after first-class acceptance criteria.",
                    List.of(),
                    List.of(),
                    List.of(),
                    provenance);
            ImplementationTask task = new ImplementationTask(
                    TaskId.generate(),
                    change.id(),
                    Optional.of("TASK-M15-QUALITY"),
                    "Implement M15 quality",
                    false,
                    provenance);
            SnapshotBusinessContent content = new SnapshotBusinessContent(
                    activeSnapshotId,
                    activeVersionId,
                    List.of(specification),
                    List.of(),
                    List.of(change),
                    List.of(),
                    List.of(),
                    List.of(task),
                    List.of(),
                    List.of(evidence));
            List<TraceabilityLink> links = List.of(
                    link(
                            new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, change.id().value()),
                            TraceabilityRelationType.AFFECTS,
                            new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirement.id().value()),
                            evidence.id()),
                    link(
                            new TraceabilityEntityRef(TraceabilityEntityKind.IMPLEMENTATION_TASK, task.id().value()),
                            TraceabilityRelationType.IMPLEMENTS,
                            new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirement.id().value()),
                            evidence.id()));
            return new Fixture(
                    projectId,
                    activeSnapshotId,
                    readySnapshotId,
                    activeVersionId,
                    readyVersionId,
                    requirement,
                    content,
                    links);
        }

        private static TraceabilityLink link(
                TraceabilityEntityRef source,
                TraceabilityRelationType relationType,
                TraceabilityEntityRef target,
                EvidenceId evidenceId) {
            return new TraceabilityLink(
                    TraceabilityLinkId.generate(),
                    source,
                    relationType,
                    target,
                    TraceabilityLinkOrigin.DERIVED,
                    TraceabilityResolutionState.RESOLVED,
                    Optional.empty(),
                    Set.of(evidenceId),
                    T0);
        }
    }
}
