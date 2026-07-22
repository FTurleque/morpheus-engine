package com.morpheus.architecture;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.application.temporal.CurrentRequirementQueryService;
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
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionedRequirementPersistenceTest {
    private static final Instant T0 = Instant.parse("2026-07-22T12:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryStoreRoundTripsVersionedRequirement() {
        var store = new MemorySpecificationKnowledgeStore();
        verifyRoundTrip(store, store);
    }

    @Test
    void sqliteStoreRoundTripsVersionedRequirement() {
        Path database = tempDir.resolve("roundtrip.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            verifyRoundTrip(snapshots, requirements);
        }
    }

    @Test
    void memoryStoreAllowsConcurrentProposalsAndRejectsInvalidOwnership() {
        var store = new MemorySpecificationKnowledgeStore();
        verifyProposalAndOwnershipRules(store, store);
    }

    @Test
    void sqliteStoreAllowsConcurrentProposalsAndRejectsInvalidOwnership() {
        Path database = tempDir.resolve("rules.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            verifyProposalAndOwnershipRules(snapshots, requirements);
        }
    }

    @Test
    void memoryCurrentQueryUsesOnlyActiveSnapshotAndCurrentTemporalState() {
        var store = new MemorySpecificationKnowledgeStore();
        verifyCurrentQueryIsolation(store, store);
    }

    @Test
    void sqliteCurrentQueryUsesOnlyActiveSnapshotAndCurrentTemporalState() {
        Path database = tempDir.resolve("current-query.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            verifyCurrentQueryIsolation(snapshots, requirements);
        }
    }

    @Test
    void sqliteReopenPreservesCurrentAndProposedSeparation() {
        Path database = tempDir.resolve("reopen.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        RequirementId logicalId = RequirementId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersion version = specificationVersion(versionId, projectId, 1L, Optional.empty());
        RequirementVersionRecord current = requirementVersion(
                snapshotId, versionId, logicalId, specificationId, TemporalState.CURRENT, "retain for 30 days");
        RequirementVersionRecord proposed = requirementVersion(
                snapshotId, versionId, logicalId, specificationId, TemporalState.PROPOSED, "retain for 60 days");

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
            snapshots.putSnapshot(readySnapshot(snapshotId, projectId, Optional.empty(), "revision-1"));
            requirements.putSpecificationVersion(version);
            requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
            requirements.putRequirementVersion(current);
            requirements.putRequirementVersion(proposed);
            snapshots.activateSnapshot(snapshotId, Optional.empty());
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            assertEquals(version, requirements.findSpecificationVersion(versionId).orElseThrow());
            assertEquals(2, requirements.listRequirementVersions(snapshotId).size());
            assertEquals(current, requirements.currentRequirement(snapshotId, logicalId.value()).orElseThrow());
            assertEquals(
                    current,
                    new CurrentRequirementQueryService(snapshots, requirements)
                            .current(projectId, logicalId.value())
                            .orElseThrow());
            assertTrue(requirements.listRequirementVersions(snapshotId).contains(proposed));
        }
    }

    private void verifyRoundTrip(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        RequirementId logicalId = RequirementId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        SpecificationVersion version = specificationVersion(versionId, projectId, 1L, Optional.empty());
        RequirementVersionRecord record = requirementVersion(
                snapshotId, versionId, logicalId, specificationId, TemporalState.CURRENT, "retain for 30 days");

        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        snapshots.putSnapshot(readySnapshot(snapshotId, projectId, Optional.empty(), "revision-1"));
        requirements.putSpecificationVersion(version);
        requirements.putSpecificationVersion(version);
        assertEquals(version, requirements.findSpecificationVersion(versionId).orElseThrow());

        SnapshotSpecificationVersionBinding binding = new SnapshotSpecificationVersionBinding(snapshotId, versionId);
        requirements.bindSnapshotVersion(binding);
        requirements.bindSnapshotVersion(binding);
        assertEquals(binding, requirements.findSnapshotVersion(snapshotId).orElseThrow());

        requirements.putRequirementVersion(record);
        requirements.putRequirementVersion(record);
        assertEquals(record, requirements.findRequirementVersion(record.entityVersion().id()).orElseThrow());
        assertEquals(1, requirements.listRequirementVersions(snapshotId).size());
        assertEquals(record, requirements.currentRequirement(snapshotId, logicalId.value()).orElseThrow());
    }

    private void verifyProposalAndOwnershipRules(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SpecificationVersionId versionOneId = SpecificationVersionId.generate();
        SpecificationVersionId versionTwoId = SpecificationVersionId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        RequirementId logicalId = RequirementId.generate();
        SpecificationId specificationId = SpecificationId.generate();

        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        snapshots.putSnapshot(readySnapshot(snapshotId, projectId, Optional.empty(), "revision-1"));
        requirements.putSpecificationVersion(specificationVersion(versionOneId, projectId, 1L, Optional.empty()));
        requirements.putSpecificationVersion(specificationVersion(
                versionTwoId, projectId, 2L, Optional.of(versionOneId)));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionOneId));

        RequirementVersionRecord current = requirementVersion(
                snapshotId, versionOneId, logicalId, specificationId, TemporalState.CURRENT, "retain for 30 days");
        RequirementVersionRecord proposed60 = requirementVersion(
                snapshotId, versionOneId, logicalId, specificationId, TemporalState.PROPOSED, "retain for 60 days");
        RequirementVersionRecord proposed15 = requirementVersion(
                snapshotId, versionOneId, logicalId, specificationId, TemporalState.PROPOSED, "retain for 15 days");

        requirements.putRequirementVersion(current);
        requirements.putRequirementVersion(proposed60);
        requirements.putRequirementVersion(proposed15);
        assertEquals(3, requirements.listRequirementVersions(snapshotId).size());

        RequirementVersionRecord secondCurrent = requirementVersion(
                snapshotId, versionOneId, logicalId, specificationId, TemporalState.CURRENT, "retain for 45 days");
        assertThrows(KnowledgeStoreException.class, () -> requirements.putRequirementVersion(secondCurrent));

        RequirementVersionRecord wrongVersion = requirementVersion(
                snapshotId, versionTwoId, logicalId, specificationId, TemporalState.PROPOSED, "retain for 90 days");
        assertThrows(KnowledgeStoreException.class, () -> requirements.putRequirementVersion(wrongVersion));
        assertThrows(
                KnowledgeStoreException.class,
                () -> requirements.bindSnapshotVersion(
                        new SnapshotSpecificationVersionBinding(snapshotId, versionTwoId)));
    }

    private void verifyCurrentQueryIsolation(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        RequirementId logicalId = RequirementId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        KnowledgeSnapshotId activeId = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId candidateId = KnowledgeSnapshotId.generate();

        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        snapshots.putSnapshot(readySnapshot(activeId, projectId, Optional.empty(), "revision-1"));
        snapshots.activateSnapshot(activeId, Optional.empty());
        snapshots.putSnapshot(readySnapshot(candidateId, projectId, Optional.of(activeId), "revision-2"));

        SpecificationVersion version = specificationVersion(versionId, projectId, 1L, Optional.empty());
        requirements.putSpecificationVersion(version);
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(activeId, versionId));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(candidateId, versionId));

        RequirementVersionRecord activeCurrent = requirementVersion(
                activeId, versionId, logicalId, specificationId, TemporalState.CURRENT, "retain for 30 days");
        RequirementVersionRecord activeProposed = requirementVersion(
                activeId, versionId, logicalId, specificationId, TemporalState.PROPOSED, "retain for 60 days");
        RequirementVersionRecord candidateCurrent = requirementVersion(
                candidateId, versionId, logicalId, specificationId, TemporalState.CURRENT, "retain for 90 days");
        requirements.putRequirementVersion(activeCurrent);
        requirements.putRequirementVersion(activeProposed);
        requirements.putRequirementVersion(candidateCurrent);

        CurrentRequirementQueryService query = new CurrentRequirementQueryService(snapshots, requirements);
        assertEquals(activeCurrent, query.current(projectId, logicalId.value()).orElseThrow());
        assertTrue(requirements.listRequirementVersions(activeId).contains(activeProposed));
        assertTrue(requirements.listRequirementVersions(candidateId).contains(candidateCurrent));
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
                Optional.of("source-revision-" + sequence),
                T0.plusSeconds(sequence),
                predecessor);
    }

    private KnowledgeSnapshotMetadata readySnapshot(
            KnowledgeSnapshotId id,
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessor,
            String revision) {
        return new KnowledgeSnapshotMetadata(
                id,
                projectId,
                predecessor,
                KnowledgeSnapshotState.READY,
                Optional.of(revision),
                T0);
    }

    private RequirementVersionRecord requirementVersion(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId specificationVersionId,
            RequirementId logicalId,
            SpecificationId specificationId,
            TemporalState state,
            String statement) {
        Requirement requirement = new Requirement(
                logicalId,
                specificationId,
                Optional.of("RETENTION"),
                "Invoice retention",
                statement,
                new Provenance(
                        new ProviderId("test-provider"),
                        Optional.of("1"),
                        SourceLocator.file("specs/billing.md"),
                        Optional.of("REQ-RETENTION"),
                        Optional.of("source-revision"),
                        EvidenceId.generate()));
        return new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        EntityVersionId.generate(),
                        logicalId.value(),
                        specificationVersionId,
                        state,
                        requirement));
    }
}
