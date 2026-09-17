package com.morpheus.architecture;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedCurrentRequirementStoreTest {
    private static final Instant T0 = Instant.parse("2026-09-08T08:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryStoreFiltersCurrentRowsBeforeApplyingLimit() {
        var store = new MemorySpecificationKnowledgeStore();
        verifyBoundedCurrentListing(store, store);
    }

    @Test
    void sqliteStoreFiltersCurrentRowsBeforeApplyingLimit() {
        Path database = tempDir.resolve("bounded-current.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            verifyBoundedCurrentListing(snapshots, requirements);
        }
    }

    private void verifyBoundedCurrentListing(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId otherSnapshotId = KnowledgeSnapshotId.generate();

        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        snapshots.putSnapshot(readySnapshot(snapshotId, projectId, "revision-1"));
        snapshots.putSnapshot(readySnapshot(otherSnapshotId, projectId, "revision-2"));

        requirements.putSpecificationVersion(new SpecificationVersion(
                versionId,
                projectId,
                Optional.of(1L),
                Optional.of("provider-v1"),
                Optional.of("source-revision-1"),
                T0,
                Optional.empty()));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(otherSnapshotId, versionId));

        RequirementVersionRecord currentA = requirementVersion(
                snapshotId, versionId, RequirementId.generate(), specificationId, TemporalState.CURRENT, "retain A");
        RequirementVersionRecord proposed = requirementVersion(
                snapshotId, versionId, currentA.entityVersion().content().id(), specificationId,
                TemporalState.PROPOSED, "retain proposed");
        RequirementVersionRecord currentB = requirementVersion(
                snapshotId, versionId, RequirementId.generate(), specificationId, TemporalState.CURRENT, "retain B");
        RequirementVersionRecord otherSnapshotCurrent = requirementVersion(
                otherSnapshotId, versionId, RequirementId.generate(), specificationId,
                TemporalState.CURRENT, "retain elsewhere");

        requirements.putRequirementVersion(currentA);
        requirements.putRequirementVersion(proposed);
        requirements.putRequirementVersion(currentB);
        requirements.putRequirementVersion(otherSnapshotCurrent);

        List<RequirementVersionRecord> expected = List.of(currentA, currentB).stream()
                .sorted(Comparator.comparing(record -> record.entityVersion().id()))
                .toList();
        List<RequirementVersionRecord> allCurrent = requirements.listCurrentRequirementVersions(snapshotId, 10);
        assertEquals(expected, allCurrent);
        assertTrue(allCurrent.stream().allMatch(record -> record.entityVersion().temporalState() == TemporalState.CURRENT));
        assertTrue(allCurrent.stream().allMatch(record -> record.snapshotId().equals(snapshotId)));

        assertEquals(expected.subList(0, 1), requirements.listCurrentRequirementVersions(snapshotId, 1));
        assertThrows(IllegalArgumentException.class,
                () -> requirements.listCurrentRequirementVersions(snapshotId, 0));
    }

    private KnowledgeSnapshotMetadata readySnapshot(
            KnowledgeSnapshotId id,
            ProjectSpecificationId projectId,
            String revision) {
        return new KnowledgeSnapshotMetadata(
                id,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of(revision),
                T0);
    }

    private RequirementVersionRecord requirementVersion(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
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
                        versionId,
                        state,
                        requirement));
    }
}
