package com.morpheus.architecture;

import com.morpheus.application.quality.QualityEvidenceKind;
import com.morpheus.application.quality.QualityFinding;
import com.morpheus.application.quality.QualityFindingCode;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.RequirementTraceabilityCoverage;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.SpecificationId;
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
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementQualityContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T18:45:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteProduceExactlySamePublishedCoverage() {
        Fixture fixture = Fixture.create();

        var memorySnapshots = new MemorySpecificationKnowledgeStore();
        var memoryTraceability = new MemoryTraceabilityStore(memorySnapshots);
        populate(memorySnapshots, memorySnapshots, memoryTraceability, fixture);
        RequirementTraceabilityCoverage memory = service(memorySnapshots, memorySnapshots, memoryTraceability)
                .assessActive(fixture.projectId())
                .orElseThrow();

        Path database = tempDir.resolve("quality-parity.db");
        RequirementTraceabilityCoverage sqlite;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            populate(snapshots, requirements, traceability, fixture);
            sqlite = service(snapshots, requirements, traceability)
                    .assessActive(fixture.projectId())
                    .orElseThrow();
        }

        assertEquals(memory, sqlite);
        assertEquals(3, memory.totalRequirements());
        assertEquals(2, memory.linkedRequirements());
        assertEquals(1, memory.orphanRequirements());
        assertEquals(2.0 / 3.0, memory.coverageRatio());
    }

    @Test
    void incomingOrOutgoingDirectLinkCoversRequirementAndProposedDoesNotEnterPopulation() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var traceability = new MemoryTraceabilityStore(snapshots);
        populate(snapshots, snapshots, traceability, fixture);

        RequirementTraceabilityCoverage coverage = service(snapshots, snapshots, traceability)
                .assessActive(fixture.projectId())
                .orElseThrow();

        assertEquals(3, coverage.totalRequirements());
        assertEquals(2, coverage.linkedRequirements());
        assertEquals(List.of(fixture.orphanRequirementId()), orphanIds(coverage));
        assertFalse(orphanIds(coverage).contains(fixture.proposedRequirementId()));
    }

    @Test
    void orphanFindingIsDeterministicAndRetainsRequirementEvidence() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var traceability = new MemoryTraceabilityStore(snapshots);
        populate(snapshots, snapshots, traceability, fixture);

        QualityFinding finding = service(snapshots, snapshots, traceability)
                .assessActive(fixture.projectId())
                .orElseThrow()
                .findings()
                .getFirst();

        assertEquals(QualityFindingCode.ORPHAN_REQUIREMENT, finding.code());
        assertEquals(DiagnosticSeverity.WARNING, finding.severity());
        assertEquals(QualityEvidenceKind.DETERMINISTIC, finding.evidenceKind());
        assertTrue(finding.confidence().isEmpty());
        assertEquals(
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, fixture.orphanRequirementId().value()),
                finding.subject());
        assertEquals(List.of(fixture.orphanEvidenceId()), finding.evidenceIds());
        assertEquals(fixture.orphanRequirementId().toString(), finding.details().get("requirementId"));
    }

    @Test
    void explicitRetiredSnapshotIsAllowedAndReadyOrUnknownSnapshotIsRejected() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var traceability = new MemoryTraceabilityStore(snapshots);
        populate(snapshots, snapshots, traceability, fixture);
        RequirementQualityService service = service(snapshots, snapshots, traceability);

        RequirementTraceabilityCoverage retired = service.assessSnapshot(fixture.retiredSnapshotId());
        assertEquals(KnowledgeSnapshotState.RETIRED, retired.snapshot().state());
        assertEquals(1, retired.totalRequirements());
        assertEquals(1, retired.orphanRequirements());
        assertEquals(0.0, retired.coverageRatio());

        assertThrows(KnowledgeStoreException.class, () -> service.assessSnapshot(fixture.readySnapshotId()));
        assertThrows(KnowledgeStoreException.class, () -> service.assessSnapshot(KnowledgeSnapshotId.generate()));
    }

    @Test
    void missingActiveIsDistinctFromEmptyPublishedRequirementSet() {
        var emptyStore = new MemorySpecificationKnowledgeStore();
        var emptyTraceability = new MemoryTraceabilityStore(emptyStore);
        assertTrue(service(emptyStore, emptyStore, emptyTraceability)
                .assessActive(ProjectSpecificationId.generate())
                .isEmpty());

        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        emptyStore.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("empty-workspace")));
        emptyStore.putSpecificationVersion(specificationVersion(versionId, projectId, 1L, Optional.empty()));
        emptyStore.putSnapshot(readySnapshot(snapshotId, projectId, Optional.empty(), "empty", T0));
        emptyStore.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        emptyStore.activateSnapshot(snapshotId, Optional.empty());

        RequirementTraceabilityCoverage coverage = service(emptyStore, emptyStore, emptyTraceability)
                .assessActive(projectId)
                .orElseThrow();
        assertEquals(0, coverage.totalRequirements());
        assertEquals(0, coverage.orphanRequirements());
        assertEquals(1.0, coverage.coverageRatio());
        assertTrue(coverage.findings().isEmpty());
    }

    @Test
    void qualityFindingContractNeverConfusesDeterministicAndHeuristicEvidence() {
        TraceabilityEntityRef subject = new TraceabilityEntityRef(
                TraceabilityEntityKind.REQUIREMENT, DomainIdentity.generate());
        EvidenceId evidenceId = EvidenceId.generate();

        assertThrows(IllegalArgumentException.class, () -> new QualityFinding(
                QualityFindingCode.ORPHAN_REQUIREMENT,
                DiagnosticSeverity.WARNING,
                QualityEvidenceKind.HEURISTIC,
                subject,
                "heuristic",
                Map.of(),
                Optional.empty(),
                List.of(evidenceId)));
        assertThrows(IllegalArgumentException.class, () -> new QualityFinding(
                QualityFindingCode.ORPHAN_REQUIREMENT,
                DiagnosticSeverity.WARNING,
                QualityEvidenceKind.DETERMINISTIC,
                subject,
                "deterministic",
                Map.of(),
                Optional.of(0.9),
                List.of(evidenceId)));
        assertThrows(IllegalArgumentException.class, () -> new QualityFinding(
                QualityFindingCode.ORPHAN_REQUIREMENT,
                DiagnosticSeverity.WARNING,
                QualityEvidenceKind.HEURISTIC,
                subject,
                "invalid confidence",
                Map.of(),
                Optional.of(1.1),
                List.of(evidenceId)));

        QualityFinding heuristic = new QualityFinding(
                QualityFindingCode.ORPHAN_REQUIREMENT,
                DiagnosticSeverity.INFO,
                QualityEvidenceKind.HEURISTIC,
                subject,
                "explicitly heuristic",
                Map.of(),
                Optional.of(0.7),
                List.of(evidenceId, evidenceId));
        assertEquals(Optional.of(0.7), heuristic.confidence());
        assertEquals(List.of(evidenceId), heuristic.evidenceIds());
    }

    @Test
    void sqliteReopenPreservesExactlySameCoverageResult() {
        Fixture fixture = Fixture.create();
        Path database = tempDir.resolve("quality-reopen.db");
        RequirementTraceabilityCoverage before;

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            populate(snapshots, requirements, traceability, fixture);
            before = service(snapshots, requirements, traceability)
                    .assessActive(fixture.projectId())
                    .orElseThrow();
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            RequirementTraceabilityCoverage after = service(snapshots, requirements, traceability)
                    .assessActive(fixture.projectId())
                    .orElseThrow();
            assertEquals(before, after);
        }
    }

    private RequirementQualityService service(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            TraceabilityStore traceability) {
        return new RequirementQualityService(snapshots, requirements, traceability);
    }

    private void populate(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            TraceabilityStore traceability,
            Fixture fixture) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace-m6")));

        requirements.putSpecificationVersion(specificationVersion(
                fixture.retiredVersionId(), fixture.projectId(), 1L, Optional.empty()));
        snapshots.putSnapshot(readySnapshot(
                fixture.retiredSnapshotId(), fixture.projectId(), Optional.empty(), "revision-1", T0));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.retiredSnapshotId(), fixture.retiredVersionId()));
        requirements.putRequirementVersion(fixture.retiredRecord());
        snapshots.activateSnapshot(fixture.retiredSnapshotId(), Optional.empty());

        requirements.putSpecificationVersion(specificationVersion(
                fixture.activeVersionId(), fixture.projectId(), 2L, Optional.of(fixture.retiredVersionId())));
        snapshots.putSnapshot(readySnapshot(
                fixture.activeSnapshotId(), fixture.projectId(), Optional.of(fixture.retiredSnapshotId()), "revision-2", T0.plusSeconds(10)));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.activeSnapshotId(), fixture.activeVersionId()));
        fixture.activeCurrentRecords().forEach(requirements::putRequirementVersion);
        requirements.putRequirementVersion(fixture.proposedRecord());
        fixture.activeLinks().forEach(link -> traceability.putLink(fixture.activeSnapshotId(), link));
        snapshots.activateSnapshot(fixture.activeSnapshotId(), Optional.of(fixture.retiredSnapshotId()));

        requirements.putSpecificationVersion(specificationVersion(
                fixture.readyVersionId(), fixture.projectId(), 3L, Optional.of(fixture.activeVersionId())));
        snapshots.putSnapshot(readySnapshot(
                fixture.readySnapshotId(), fixture.projectId(), Optional.of(fixture.activeSnapshotId()), "revision-3", T0.plusSeconds(20)));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.readySnapshotId(), fixture.readyVersionId()));
        requirements.putRequirementVersion(fixture.readyRecord());
    }

    private static SpecificationVersion specificationVersion(
            SpecificationVersionId id,
            ProjectSpecificationId projectId,
            long sequence,
            Optional<SpecificationVersionId> predecessor) {
        return new SpecificationVersion(
                id,
                projectId,
                Optional.of(sequence),
                Optional.of("provider-v1"),
                Optional.of("source-revision-" + sequence),
                T0.plusSeconds(sequence),
                predecessor);
    }

    private static KnowledgeSnapshotMetadata readySnapshot(
            KnowledgeSnapshotId id,
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessor,
            String revision,
            Instant createdAt) {
        return new KnowledgeSnapshotMetadata(
                id,
                projectId,
                predecessor,
                KnowledgeSnapshotState.READY,
                Optional.of(revision),
                createdAt);
    }

    private static RequirementVersionRecord record(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId specificationVersionId,
            EntityVersionId entityVersionId,
            RequirementId requirementId,
            SpecificationId specificationId,
            TemporalState state,
            String key,
            EvidenceId evidenceId) {
        Requirement requirement = new Requirement(
                requirementId,
                specificationId,
                Optional.of(key),
                key,
                "Statement for " + key,
                new Provenance(
                        new ProviderId("m6-fixture"),
                        Optional.of("1"),
                        SourceLocator.file("specs/m6.md"),
                        Optional.of(key),
                        Optional.of("revision"),
                        evidenceId));
        return new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        entityVersionId,
                        requirementId.value(),
                        specificationVersionId,
                        state,
                        requirement));
    }

    private static TraceabilityLink link(
            TraceabilityLinkId id,
            TraceabilityEntityRef source,
            TraceabilityRelationType relationType,
            TraceabilityEntityRef target,
            EvidenceId evidenceId,
            Instant observedAt) {
        return new TraceabilityLink(
                id,
                source,
                relationType,
                target,
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(evidenceId),
                observedAt);
    }

    private List<RequirementId> orphanIds(RequirementTraceabilityCoverage coverage) {
        return coverage.findings().stream()
                .map(finding -> new RequirementId(finding.subject().identity()))
                .toList();
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
            RequirementVersionRecord retiredRecord,
            List<RequirementVersionRecord> activeCurrentRecords,
            RequirementVersionRecord proposedRecord,
            RequirementVersionRecord readyRecord,
            List<TraceabilityLink> activeLinks,
            RequirementId orphanRequirementId,
            RequirementId proposedRequirementId,
            EvidenceId orphanEvidenceId) {

        static Fixture create() {
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            SpecificationId specificationId = SpecificationId.generate();
            KnowledgeSnapshotId retiredSnapshotId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId activeSnapshotId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId readySnapshotId = KnowledgeSnapshotId.generate();
            SpecificationVersionId retiredVersionId = SpecificationVersionId.generate();
            SpecificationVersionId activeVersionId = SpecificationVersionId.generate();
            SpecificationVersionId readyVersionId = SpecificationVersionId.generate();

            RequirementId retiredId = RequirementId.generate();
            RequirementVersionRecord retired = record(
                    retiredSnapshotId, retiredVersionId, EntityVersionId.generate(), retiredId, specificationId,
                    TemporalState.CURRENT, "REQ-OLD", EvidenceId.generate());

            RequirementId incomingId = RequirementId.generate();
            RequirementId outgoingId = RequirementId.generate();
            RequirementId orphanId = RequirementId.generate();
            EvidenceId orphanEvidence = EvidenceId.generate();
            RequirementVersionRecord incoming = record(
                    activeSnapshotId, activeVersionId, EntityVersionId.generate(), incomingId, specificationId,
                    TemporalState.CURRENT, "REQ-IN", EvidenceId.generate());
            RequirementVersionRecord outgoing = record(
                    activeSnapshotId, activeVersionId, EntityVersionId.generate(), outgoingId, specificationId,
                    TemporalState.CURRENT, "REQ-OUT", EvidenceId.generate());
            RequirementVersionRecord orphan = record(
                    activeSnapshotId, activeVersionId, EntityVersionId.generate(), orphanId, specificationId,
                    TemporalState.CURRENT, "REQ-ORPHAN", orphanEvidence);

            RequirementId proposedId = RequirementId.generate();
            RequirementVersionRecord proposed = record(
                    activeSnapshotId, activeVersionId, EntityVersionId.generate(), proposedId, specificationId,
                    TemporalState.PROPOSED, "REQ-PROPOSED", EvidenceId.generate());

            RequirementVersionRecord ready = record(
                    readySnapshotId, readyVersionId, EntityVersionId.generate(), RequirementId.generate(), specificationId,
                    TemporalState.CURRENT, "REQ-FUTURE", EvidenceId.generate());

            TraceabilityLink incomingLink = link(
                    TraceabilityLinkId.generate(),
                    new TraceabilityEntityRef(TraceabilityEntityKind.SCENARIO, DomainIdentity.generate()),
                    TraceabilityRelationType.REFINES,
                    new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, incomingId.value()),
                    EvidenceId.generate(),
                    T0.plusSeconds(11));
            TraceabilityLink outgoingLink = link(
                    TraceabilityLinkId.generate(),
                    new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, outgoingId.value()),
                    TraceabilityRelationType.DERIVES_FROM,
                    new TraceabilityEntityRef(TraceabilityEntityKind.SPECIFICATION, specificationId.value()),
                    EvidenceId.generate(),
                    T0.plusSeconds(12));

            return new Fixture(
                    projectId,
                    specificationId,
                    retiredSnapshotId,
                    activeSnapshotId,
                    readySnapshotId,
                    retiredVersionId,
                    activeVersionId,
                    readyVersionId,
                    retired,
                    List.of(incoming, outgoing, orphan),
                    proposed,
                    ready,
                    List.of(incomingLink, outgoingLink),
                    orphanId,
                    proposedId,
                    orphanEvidence);
        }
    }
}
