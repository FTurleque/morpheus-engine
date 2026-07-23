package com.morpheus.architecture;

import com.morpheus.application.quality.AcceptanceCoverageStatus;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.LifecycleQualityAggregationStatus;
import com.morpheus.application.quality.QualityEvidenceKind;
import com.morpheus.application.quality.QualityFindingCode;
import com.morpheus.application.quality.QualityReport;
import com.morpheus.application.quality.QualityReportMetrics;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.quality.compact.CompactQualityReportService;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.application.traceability.ExternalTraceabilityLinkFactory;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.reference.ResolvedExternalTarget;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateQualityReportContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T21:05:00Z");
    private static final ExternalTraceabilityLinkFactory EXTERNAL_LINK_FACTORY = new ExternalTraceabilityLinkFactory();

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteProduceSameAggregateReportWithExactMetrics() {
        Fixture fixture = Fixture.create();

        var memoryCore = new MemorySpecificationKnowledgeStore();
        var memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        var memoryTrace = new MemoryTraceabilityStore(memoryCore);
        var memoryReferences = new MemoryExternalReferenceStore(memoryCore);
        seed(memoryCore, memoryCore, memoryContent, memoryTrace, memoryReferences, fixture);
        QualityReport memory = service(memoryCore, memoryContent, memoryCore, memoryTrace, memoryReferences)
                .assessActive(fixture.projectId()).orElseThrow();

        Path database = tempDir.resolve("aggregate-quality-parity.db");
        QualityReport sqlite;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            seed(snapshots, versions, content, traceability, references, fixture);
            sqlite = service(snapshots, content, versions, traceability, references)
                    .assessActive(fixture.projectId()).orElseThrow();
        }

        assertEquals(memory, sqlite);
        QualityReportMetrics metrics = memory.metrics();
        assertEquals(10, metrics.totalFindings());
        assertEquals(2, metrics.totalRequirements());
        assertEquals(1, metrics.linkedRequirements());
        assertEquals(1, metrics.orphanRequirements());
        assertEquals(0.5, metrics.requirementCoverageRatio());
        assertEquals(2, metrics.totalTasks());
        assertEquals(1, metrics.coveredTasks());
        assertEquals(1, metrics.uncoveredTasks());
        assertEquals(0.5, metrics.taskCoverageRatio());
        assertEquals(AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL, metrics.acceptanceCoverageStatus());
        assertEquals(2, metrics.totalChanges());
        assertEquals(2, metrics.totalDesignDecisions());
        assertEquals(2, metrics.totalExternalReferences());
    }

    @Test
    void findingCountsAreStableAndLifecycleAggregateRequiresExplicitInput() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);

        QualityReport report = service(core, content, core, trace, references)
                .assessActive(fixture.projectId()).orElseThrow();

        assertEquals(LifecycleQualityAggregationStatus.REQUIRES_EXPLICIT_LIFECYCLE_INPUT,
                report.lifecycleAggregationStatus());
        assertEquals(Map.of(
                QualityFindingCode.ORPHAN_REQUIREMENT, 1,
                QualityFindingCode.IMPLEMENTATION_TASK_WITHOUT_REQUIREMENT, 1,
                QualityFindingCode.ACCEPTANCE_COVERAGE_UNAVAILABLE, 1,
                QualityFindingCode.CHANGE_WITHOUT_CURRENT_REQUIREMENT, 1,
                QualityFindingCode.CHANGE_COMPLETENESS_PARTIALLY_OBSERVABLE, 2,
                QualityFindingCode.DESIGN_DECISION_WITHOUT_TRACE, 1,
                QualityFindingCode.DECISION_JUSTIFICATION_UNAVAILABLE, 2,
                QualityFindingCode.EXTERNAL_REFERENCE_UNRESOLVED, 1),
                report.metrics().findingsByCode());
        assertEquals(Map.of(DiagnosticSeverity.WARNING, 6, DiagnosticSeverity.INFO, 4),
                report.metrics().findingsBySeverity());
        assertEquals(Map.of(QualityEvidenceKind.DETERMINISTIC, 10),
                report.metrics().findingsByEvidenceKind());
        assertEquals(report.findings().stream().distinct().sorted().toList(), report.findings());
    }

    @Test
    void compactViewPreservesSnapshotMetricsAndFindingsWithCanonicalJson() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);
        QualityReport report = service(core, content, core, trace, references)
                .assessActive(fixture.projectId()).orElseThrow();

        CompactQualityReportService compact = new CompactQualityReportService();
        var view = compact.view(report);
        String first = compact.toJson(report);
        String second = compact.toJson(report);

        assertEquals(1, view.query().schemaVersion());
        assertEquals("get_quality_report", view.query().operation());
        assertEquals(report.snapshot().id().toString(), view.snapshot().snapshotId());
        assertEquals(10, view.metrics().totalFindings());
        assertEquals("REQUIRES_EXPLICIT_LIFECYCLE_INPUT", view.metrics().lifecycleAggregationStatus());
        assertEquals(10, view.findings().size());
        assertTrue(view.findings().stream().allMatch(item -> !item.evidenceIds().isEmpty()));
        assertEquals(first, second);
        assertArrayEquals(compact.toUtf8(report), compact.toUtf8(report));
        assertTrue(first.contains("\"operation\":\"get_quality_report\""));
        assertTrue(first.contains("\"totalFindings\":10"));
        assertTrue(first.contains("\"EXTERNAL_REFERENCE_UNRESOLVED\":1"));
    }

    @Test
    void activeRetiredReadyUnknownAndMissingActivePoliciesRemainExplicit() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);
        QualityReportService service = service(core, content, core, trace, references);

        QualityReport active = service.assessActive(fixture.projectId()).orElseThrow();
        assertEquals(fixture.activeSnapshotId(), active.snapshot().id());
        assertEquals(KnowledgeSnapshotState.ACTIVE, active.snapshot().state());
        assertEquals(KnowledgeSnapshotState.RETIRED,
                service.assessSnapshot(fixture.retiredSnapshotId()).snapshot().state());
        assertThrows(KnowledgeStoreException.class, () -> service.assessSnapshot(fixture.readySnapshotId()));
        assertThrows(KnowledgeStoreException.class, () -> service.assessSnapshot(KnowledgeSnapshotId.generate()));

        var empty = new MemorySpecificationKnowledgeStore();
        var emptyContent = new MemorySnapshotBusinessContentStore(empty, empty);
        var emptyTrace = new MemoryTraceabilityStore(empty);
        var emptyReferences = new MemoryExternalReferenceStore(empty);
        assertTrue(service(empty, emptyContent, empty, emptyTrace, emptyReferences)
                .assessActive(ProjectSpecificationId.generate()).isEmpty());
    }

    @Test
    void sqliteReopenPreservesAggregateReportAndCanonicalJson() {
        Fixture fixture = Fixture.create();
        Path database = tempDir.resolve("aggregate-quality-reopen.db");
        QualityReport before;
        String jsonBefore;

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            seed(snapshots, versions, content, traceability, references, fixture);
            before = service(snapshots, content, versions, traceability, references)
                    .assessActive(fixture.projectId()).orElseThrow();
            jsonBefore = new CompactQualityReportService().toJson(before);
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            QualityReport after = service(snapshots, content, versions, traceability, references)
                    .assessActive(fixture.projectId()).orElseThrow();
            assertEquals(before, after);
            assertEquals(jsonBefore, new CompactQualityReportService().toJson(after));
        }
    }

    @Test
    void aggregateContractRejectsTruncatedFindingsOrInconsistentMetricCounts() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);
        QualityReport report = service(core, content, core, trace, references)
                .assessActive(fixture.projectId()).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> new QualityReport(
                report.snapshot(),
                report.requirements(),
                report.tasks(),
                report.acceptance(),
                report.changes(),
                report.decisionsAndReferences(),
                report.lifecycleAggregationStatus(),
                report.metrics(),
                List.of()));

        QualityReportMetrics metrics = report.metrics();
        assertThrows(IllegalArgumentException.class, () -> new QualityReportMetrics(
                metrics.totalFindings() + 1,
                metrics.totalRequirements(),
                metrics.linkedRequirements(),
                metrics.orphanRequirements(),
                metrics.requirementCoverageRatio(),
                metrics.totalTasks(),
                metrics.coveredTasks(),
                metrics.uncoveredTasks(),
                metrics.taskCoverageRatio(),
                metrics.acceptanceCoverageStatus(),
                metrics.totalChanges(),
                metrics.totalDesignDecisions(),
                metrics.totalExternalReferences(),
                metrics.findingsByCode(),
                metrics.findingsBySeverity(),
                metrics.findingsByEvidenceKind()));
    }

    private QualityReportService service(
            SpecificationKnowledgeStore snapshots,
            SnapshotBusinessContentStore content,
            VersionedRequirementStore versions,
            TraceabilityStore traceability,
            ExternalReferenceStore references) {
        return new QualityReportService(
                snapshots,
                new RequirementQualityService(snapshots, versions, traceability),
                new TaskQualityService(snapshots, content, versions, traceability),
                new AcceptanceQualityService(snapshots, content),
                new ChangeCompletenessService(snapshots, content, versions, traceability),
                new DecisionReferenceQualityService(snapshots, content, versions, traceability, references));
    }

    private void seed(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            SnapshotBusinessContentStore content,
            TraceabilityStore traceability,
            ExternalReferenceStore references,
            Fixture fixture) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace-m6-s5")));

        putCandidate(
                snapshots, versions, content,
                fixture.retiredSnapshotId(), fixture.retiredVersionId(), Optional.empty(),
                fixture.retiredContent(), List.of(fixture.retiredRequirement()), "r1", T0);
        snapshots.activateSnapshot(fixture.retiredSnapshotId(), Optional.empty());

        putCandidate(
                snapshots, versions, content,
                fixture.activeSnapshotId(), fixture.activeVersionId(), Optional.of(fixture.retiredSnapshotId()),
                fixture.activeContent(), fixture.activeRequirements(), "r2", T0.plusSeconds(10));
        fixture.references().forEach(reference -> references.putReference(fixture.activeSnapshotId(), reference));
        fixture.links().forEach(link -> traceability.putLink(fixture.activeSnapshotId(), link));
        snapshots.activateSnapshot(fixture.activeSnapshotId(), Optional.of(fixture.retiredSnapshotId()));

        putCandidate(
                snapshots, versions, content,
                fixture.readySnapshotId(), fixture.readyVersionId(), Optional.of(fixture.activeSnapshotId()),
                fixture.readyContent(), List.of(fixture.readyRequirement()), "r3", T0.plusSeconds(20));
    }

    private void putCandidate(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            SnapshotBusinessContentStore content,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            Optional<KnowledgeSnapshotId> predecessor,
            SnapshotBusinessContent businessContent,
            List<RequirementVersionRecord> requirements,
            String revision,
            Instant createdAt) {
        ProjectSpecificationId projectId = businessContent.specifications().getFirst().projectId();
        versions.putSpecificationVersion(new SpecificationVersion(
                versionId,
                projectId,
                Optional.empty(),
                Optional.of("provider-v1"),
                Optional.of(revision),
                createdAt,
                Optional.empty()));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                predecessor,
                KnowledgeSnapshotState.READY,
                Optional.of(revision),
                createdAt));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        requirements.forEach(versions::putRequirementVersion);
        content.putSnapshotContent(businessContent);
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            SpecificationId specificationId,
            KnowledgeSnapshotId retiredSnapshotId,
            KnowledgeSnapshotId activeSnapshotId,
            KnowledgeSnapshotId readySnapshotId,
            SpecificationVersionId retiredVersionId,
            SpecificationVersionId activeVersionId,
            SpecificationVersionId readyVersionId,
            SnapshotBusinessContent retiredContent,
            SnapshotBusinessContent activeContent,
            SnapshotBusinessContent readyContent,
            RequirementVersionRecord retiredRequirement,
            List<RequirementVersionRecord> activeRequirements,
            RequirementVersionRecord readyRequirement,
            List<ExternalReference> references,
            List<TraceabilityLink> links) {

        static Fixture create() {
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            SpecificationId specificationId = SpecificationId.generate();
            KnowledgeSnapshotId retiredSnapshotId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId activeSnapshotId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId readySnapshotId = KnowledgeSnapshotId.generate();
            SpecificationVersionId retiredVersionId = SpecificationVersionId.generate();
            SpecificationVersionId activeVersionId = SpecificationVersionId.generate();
            SpecificationVersionId readyVersionId = SpecificationVersionId.generate();

            Evidence retiredEvidence = evidence("retired");
            Evidence activeEvidence = evidence("active");
            Evidence readyEvidence = evidence("ready");
            Provenance retiredProvenance = provenance(retiredEvidence.id(), "retired");
            Provenance activeProvenance = provenance(activeEvidence.id(), "active");
            Provenance readyProvenance = provenance(readyEvidence.id(), "ready");

            Specification retiredSpecification = new Specification(
                    specificationId, projectId, "Retired", "Retired specification", Optional.empty(), retiredProvenance);
            RequirementVersionRecord retiredRequirement = requirement(
                    retiredSnapshotId, retiredVersionId, specificationId, "REQ-OLD", retiredEvidence.id());
            SnapshotBusinessContent retiredContent = businessContent(
                    retiredSnapshotId, retiredVersionId, retiredSpecification,
                    List.of(), List.of(), List.of(), retiredEvidence);

            Specification activeSpecification = new Specification(
                    specificationId, projectId, "Active", "Active specification", Optional.empty(), activeProvenance);
            ChangeProposal coveredChange = change(projectId, "Covered change", activeProvenance);
            ChangeProposal uncoveredChange = change(projectId, "Uncovered change", activeProvenance);
            DesignDecision tracedDecision = new DesignDecision(
                    DesignDecisionId.generate(), coveredChange.id(), "Traced", "Use explicit trace", activeProvenance);
            DesignDecision untracedDecision = new DesignDecision(
                    DesignDecisionId.generate(), uncoveredChange.id(), "Untraced", "No trace fixture", activeProvenance);
            ImplementationTask coveredTask = new ImplementationTask(
                    TaskId.generate(), coveredChange.id(), Optional.of("TASK-COVERED"), "Covered task", false, activeProvenance);
            ImplementationTask uncoveredTask = new ImplementationTask(
                    TaskId.generate(), uncoveredChange.id(), Optional.of("TASK-UNCOVERED"), "Uncovered task", true, activeProvenance);

            RequirementVersionRecord linkedRequirement = requirement(
                    activeSnapshotId, activeVersionId, specificationId, "REQ-LINKED", EvidenceId.generate());
            RequirementVersionRecord orphanRequirement = requirement(
                    activeSnapshotId, activeVersionId, specificationId, "REQ-ORPHAN", EvidenceId.generate());

            SnapshotBusinessContent activeContent = businessContent(
                    activeSnapshotId,
                    activeVersionId,
                    activeSpecification,
                    List.of(uncoveredChange, coveredChange),
                    List.of(untracedDecision, tracedDecision),
                    List.of(uncoveredTask, coveredTask),
                    activeEvidence);

            TraceabilityEntityRef coveredChangeRef = new TraceabilityEntityRef(
                    TraceabilityEntityKind.CHANGE, coveredChange.id().value());
            TraceabilityLink affects = link(
                    coveredChangeRef,
                    TraceabilityRelationType.AFFECTS,
                    new TraceabilityEntityRef(
                            TraceabilityEntityKind.REQUIREMENT,
                            linkedRequirement.entityVersion().content().id().value()),
                    activeEvidence.id(),
                    1);
            TraceabilityLink decisionTrace = link(
                    coveredChangeRef,
                    TraceabilityRelationType.DECIDED_BY,
                    new TraceabilityEntityRef(TraceabilityEntityKind.DESIGN_DECISION, tracedDecision.id().value()),
                    activeEvidence.id(),
                    2);

            ExternalReference unresolved = ExternalReference.unvalidated(
                            ExternalReferenceId.generate(), coveredChange.id().value(), target("unresolved"), Optional.of(activeProvenance))
                    .transition(
                            ExternalReferenceResolutionState.UNRESOLVED,
                            ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                            Optional.empty(),
                            T0.plusSeconds(3));
            ExternalReference resolved = ExternalReference.unvalidated(
                            ExternalReferenceId.generate(), coveredChange.id().value(), target("resolved"), Optional.of(activeProvenance))
                    .transition(
                            ExternalReferenceResolutionState.UNRESOLVED,
                            ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                            Optional.empty(),
                            T0.plusSeconds(4))
                    .transition(
                            ExternalReferenceResolutionState.RESOLVED,
                            ExternalReferenceResolutionReason.RESOLVED,
                            Optional.of(new ResolvedExternalTarget(target("resolved"), Map.of("kind", "symbol"))),
                            T0.plusSeconds(5));
            TraceabilityLink unresolvedLink = EXTERNAL_LINK_FACTORY.create(
                    TraceabilityLinkId.generate(), coveredChangeRef, TraceabilityRelationType.LINKS_TO_CODE,
                    unresolved, TraceabilityLinkOrigin.EXPLICIT, Optional.empty(), Set.of(activeEvidence.id()), T0.plusSeconds(6));
            TraceabilityLink resolvedLink = EXTERNAL_LINK_FACTORY.create(
                    TraceabilityLinkId.generate(), coveredChangeRef, TraceabilityRelationType.LINKS_TO_TEST,
                    resolved, TraceabilityLinkOrigin.EXPLICIT, Optional.empty(), Set.of(activeEvidence.id()), T0.plusSeconds(7));

            Specification readySpecification = new Specification(
                    specificationId, projectId, "Ready", "Ready specification", Optional.empty(), readyProvenance);
            RequirementVersionRecord readyRequirement = requirement(
                    readySnapshotId, readyVersionId, specificationId, "REQ-READY", readyEvidence.id());
            SnapshotBusinessContent readyContent = businessContent(
                    readySnapshotId, readyVersionId, readySpecification,
                    List.of(), List.of(), List.of(), readyEvidence);

            return new Fixture(
                    projectId,
                    specificationId,
                    retiredSnapshotId,
                    activeSnapshotId,
                    readySnapshotId,
                    retiredVersionId,
                    activeVersionId,
                    readyVersionId,
                    retiredContent,
                    activeContent,
                    readyContent,
                    retiredRequirement,
                    List.of(linkedRequirement, orphanRequirement),
                    readyRequirement,
                    List.of(unresolved, resolved),
                    List.of(affects, decisionTrace, unresolvedLink, resolvedLink));
        }

        private static SnapshotBusinessContent businessContent(
                KnowledgeSnapshotId snapshotId,
                SpecificationVersionId versionId,
                Specification specification,
                List<ChangeProposal> changes,
                List<DesignDecision> decisions,
                List<ImplementationTask> tasks,
                Evidence evidence) {
            return new SnapshotBusinessContent(
                    snapshotId,
                    versionId,
                    List.of(specification),
                    List.of(),
                    changes,
                    List.of(),
                    decisions,
                    tasks,
                    List.of(evidence));
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

        private static RequirementVersionRecord requirement(
                KnowledgeSnapshotId snapshotId,
                SpecificationVersionId versionId,
                SpecificationId specificationId,
                String key,
                EvidenceId evidenceId) {
            Requirement requirement = new Requirement(
                    RequirementId.generate(),
                    specificationId,
                    Optional.of(key),
                    key,
                    "Statement for " + key,
                    provenance(evidenceId, key));
            return new RequirementVersionRecord(
                    snapshotId,
                    new EntityVersion<>(
                            EntityVersionId.generate(),
                            requirement.id().value(),
                            versionId,
                            TemporalState.CURRENT,
                            requirement));
        }

        private static TraceabilityLink link(
                TraceabilityEntityRef source,
                TraceabilityRelationType relation,
                TraceabilityEntityRef target,
                EvidenceId evidenceId,
                long seconds) {
            return new TraceabilityLink(
                    TraceabilityLinkId.generate(),
                    source,
                    relation,
                    target,
                    TraceabilityLinkOrigin.DERIVED,
                    TraceabilityResolutionState.RESOLVED,
                    Optional.empty(),
                    Set.of(evidenceId),
                    T0.plusSeconds(seconds));
        }

        private static Evidence evidence(String name) {
            return new Evidence(
                    EvidenceId.generate(),
                    SourceLocator.file("specs/" + name + ".md"),
                    Optional.empty(),
                    Optional.of("sha256:" + name));
        }

        private static Provenance provenance(EvidenceId evidenceId, String name) {
            return new Provenance(
                    new ProviderId("m6-s5-fixture"),
                    Optional.of("1"),
                    SourceLocator.file("specs/" + name + ".md"),
                    Optional.of(name),
                    Optional.of("revision-" + name),
                    evidenceId);
        }

        private static ExternalReferenceTarget target(String suffix) {
            return new ExternalReferenceTarget(
                    "MINOS",
                    Optional.of("morpheus-engine"),
                    "CODE_SYMBOL",
                    "com.morpheus." + suffix,
                    Optional.of("rev-" + suffix));
        }
    }
}
