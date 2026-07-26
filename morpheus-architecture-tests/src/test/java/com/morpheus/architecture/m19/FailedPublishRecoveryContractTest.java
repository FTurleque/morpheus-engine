package com.morpheus.architecture.m19;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.ingestion.ProjectSnapshotImportResult;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.traceability.PersistentTraceabilityLinkIdentityResolver;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailedPublishRecoveryContractTest {
    private static final Instant T0 = Instant.parse("2026-07-26T19:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void invalidCandidateNeverLeaksIntoActiveAndAValidRebuildCanPublishAfterFailure() {
        Path database = tempDir.resolve("failed-publish-recovery.db");
        ProjectSpecificationId projectId = new ProjectSpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1950, 1));
        KnowledgeSnapshotId firstActive;
        KnowledgeSnapshotId rebuiltActive;

        try (Stores stores = Stores.open(database)) {
            NormalizedProjectContent first = content(projectId, true, "v1");
            ProjectSnapshotImportResult published = stores.publisher().publishFull(first, "revision-v1", T0);
            firstActive = published.snapshot().id();
            assertEquals(firstActive, stores.snapshots().activeSnapshot(projectId).orElseThrow().id());

            NormalizedProjectContent invalid = content(projectId, false, "invalid");
            assertThrows(RuntimeException.class,
                    () -> stores.publisher().publishFull(invalid, "revision-invalid", T0.plusSeconds(1)));

            assertEquals(firstActive, stores.snapshots().activeSnapshot(projectId).orElseThrow().id(),
                    "a failed candidate must never replace the last valid ACTIVE snapshot");
            assertEquals(1L, stores.snapshots().listSnapshots(projectId).stream()
                    .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                    .count());
            assertTrue(stores.snapshots().listSnapshots(projectId).stream()
                            .anyMatch(snapshot -> snapshot.state() == KnowledgeSnapshotState.FAILED),
                    "the failed candidate must be visible as FAILED rather than disappearing or becoming ACTIVE");

            NormalizedProjectContent rebuilt = content(projectId, true, "v2");
            ProjectSnapshotImportResult rebuiltResult = stores.publisher().publishFull(
                    rebuilt,
                    "revision-v2",
                    T0.plusSeconds(2));
            rebuiltActive = rebuiltResult.snapshot().id();

            assertNotEquals(firstActive, rebuiltActive);
            assertEquals(rebuiltActive, stores.snapshots().activeSnapshot(projectId).orElseThrow().id());
            assertEquals(KnowledgeSnapshotState.RETIRED,
                    stores.snapshots().findSnapshot(firstActive).orElseThrow().state());
            assertEquals(1L, stores.snapshots().listSnapshots(projectId).stream()
                    .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                    .count());
        }

        try (SqliteSpecificationKnowledgeStore reopened = new SqliteSpecificationKnowledgeStore(database)) {
            assertEquals(rebuiltActive, reopened.activeSnapshot(projectId).orElseThrow().id());
            assertEquals(KnowledgeSnapshotState.RETIRED, reopened.findSnapshot(firstActive).orElseThrow().state());
            assertEquals(1L, reopened.listSnapshots(projectId).stream()
                    .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                    .count());
            assertTrue(reopened.listSnapshots(projectId).stream()
                    .anyMatch(snapshot -> snapshot.state() == KnowledgeSnapshotState.FAILED));
        }
    }

    private NormalizedProjectContent content(ProjectSpecificationId projectId, boolean valid, String variant) {
        ProviderId providerId = new ProviderId("m19-recovery");
        SpecificationId specificationId = new SpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1951, 1));
        ProjectSpecification project = new ProjectSpecification(
                projectId,
                "M19 recovery project",
                SourceLocator.file("m19/recovery-project"));
        Specification specification = new Specification(
                specificationId,
                projectId,
                "SPEC-RECOVERY",
                "Recovery specification",
                Optional.of("M19 failure-atomic rebuild fixture"),
                provenance(providerId, 1952, 1, "specification.md", "SPEC-RECOVERY"));

        SpecificationId requirementSpecificationId = valid
                ? specificationId
                : new SpecificationId(M19LargeFixtureSupport.deterministicIdentity(1951, 999));
        Requirement requirement = new Requirement(
                new RequirementId(M19LargeFixtureSupport.deterministicIdentity(1953, 1)),
                requirementSpecificationId,
                Optional.of("REQ-RECOVERY"),
                "Recovery requirement " + variant,
                "A failed rebuild must not replace the current published state. variant=" + variant,
                provenance(providerId, 1954, 1, "requirements/recovery.md", "REQ-RECOVERY"));

        return new NormalizedProjectContent(
                project,
                List.of(specification),
                List.of(requirement),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private Provenance provenance(
            ProviderId providerId,
            long namespace,
            long ordinal,
            String source,
            String externalId) {
        return new Provenance(
                providerId,
                Optional.of("1"),
                SourceLocator.file(source),
                Optional.of(externalId),
                Optional.of("m19-recovery"),
                new EvidenceId(M19LargeFixtureSupport.deterministicIdentity(namespace, ordinal)));
    }

    private record Stores(
            SqliteSpecificationKnowledgeStore snapshots,
            SqliteVersionedRequirementStore requirements,
            SqliteSnapshotBusinessContentStore businessContent,
            SqliteTraceabilityStore traceability,
            ProjectSnapshotImportService publisher) implements AutoCloseable {

        static Stores open(Path database) {
            SqliteSpecificationKnowledgeStore snapshots = new SqliteSpecificationKnowledgeStore(database);
            SqliteVersionedRequirementStore requirements = new SqliteVersionedRequirementStore(database);
            SqliteSnapshotBusinessContentStore business = new SqliteSnapshotBusinessContentStore(database);
            SqliteTraceabilityStore traceability = new SqliteTraceabilityStore(database);
            ProjectSnapshotImportService publisher = new ProjectSnapshotImportService(
                    snapshots,
                    requirements,
                    business,
                    traceability,
                    new PersistentTraceabilityLinkIdentityResolver(traceability));
            return new Stores(snapshots, requirements, business, traceability, publisher);
        }

        @Override
        public void close() {
            traceability.close();
            businessContent.close();
            requirements.close();
            snapshots.close();
        }
    }
}
