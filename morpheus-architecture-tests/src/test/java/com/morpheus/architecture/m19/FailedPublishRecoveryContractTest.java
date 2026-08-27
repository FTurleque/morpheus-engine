package com.morpheus.architecture.m19;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.ingestion.ProjectSnapshotImportResult;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.domain.evidence.Evidence;
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
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
            NormalizedProjectContent first = content(projectId, "v1");
            ProjectSnapshotImportResult published = stores.publisher().publishFull(first, Optional.of("revision-v1"), T0);
            firstActive = published.snapshot().id();
            assertEquals(firstActive, stores.snapshots().activeSnapshot(projectId).orElseThrow().id());
            assertEquals(List.of(1L), specificationSequences(database, projectId));

            NormalizedProjectContent interrupted = content(projectId, "persistence-failure");
            assertThrows(KnowledgeStoreException.class,
                    () -> stores.failingPublisher().publishFull(
                            interrupted, Optional.of("revision-failed"), T0.plusSeconds(1)));

            assertEquals(firstActive, stores.snapshots().activeSnapshot(projectId).orElseThrow().id(),
                    "a failed candidate must never replace the last valid ACTIVE snapshot");
            assertEquals(1L, stores.snapshots().listSnapshots(projectId).stream()
                    .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                    .count());
            assertTrue(stores.snapshots().listSnapshots(projectId).stream()
                            .anyMatch(snapshot -> snapshot.state() == KnowledgeSnapshotState.FAILED),
                    "the failed candidate must be visible as FAILED rather than disappearing or becoming ACTIVE");
            assertEquals(List.of(1L, 2L), specificationSequences(database, projectId),
                    "a failed durable candidate consumes a unique version sequence");

            NormalizedProjectContent rebuilt = content(projectId, "v2");
            ProjectSnapshotImportResult rebuiltResult = stores.publisher().publishFull(
                    rebuilt,
                    Optional.of("revision-v2"),
                    T0.plusSeconds(2));
            rebuiltActive = rebuiltResult.snapshot().id();

            assertNotEquals(firstActive, rebuiltActive);
            assertEquals(3L, rebuiltResult.specificationVersion().sequence().orElseThrow());
            assertEquals(List.of(1L, 2L, 3L), specificationSequences(database, projectId));
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
            assertEquals(List.of(1L, 2L, 3L), specificationSequences(database, projectId));
        }
    }

    private List<Long> specificationSequences(Path database, ProjectSpecificationId projectId) {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.prepareStatement("""
                     SELECT sequence
                     FROM specification_versions
                     WHERE project_id = ? AND sequence IS NOT NULL
                     ORDER BY sequence
                     """)) {
            statement.setString(1, projectId.toString());
            List<Long> sequences = new ArrayList<>();
            try (var result = statement.executeQuery()) {
                while (result.next()) sequences.add(result.getLong(1));
            }
            return List.copyOf(sequences);
        } catch (Exception failure) {
            throw new AssertionError("cannot inspect durable specification version sequences", failure);
        }
    }

    private NormalizedProjectContent content(ProjectSpecificationId projectId, String variant) {
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

        Requirement requirement = new Requirement(
                new RequirementId(M19LargeFixtureSupport.deterministicIdentity(1953, 1)),
                specificationId,
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
                List.of(
                        evidence(1952, 1, "specification.md"),
                        evidence(1954, 1, "requirements/recovery.md")),
                List.of());
    }

    private Evidence evidence(long namespace, long ordinal, String source) {
        return new Evidence(
                new EvidenceId(M19LargeFixtureSupport.deterministicIdentity(namespace, ordinal)),
                SourceLocator.file(source),
                Optional.empty(),
                Optional.empty());
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
                    traceability);
            return new Stores(snapshots, requirements, business, traceability, publisher);
        }

        ProjectSnapshotImportService failingPublisher() {
            return new ProjectSnapshotImportService(
                    snapshots,
                    requirements,
                    businessContent,
                    new FailingTraceabilityStore(traceability));
        }

        @Override
        public void close() {
            traceability.close();
            businessContent.close();
            requirements.close();
            snapshots.close();
        }
    }

    private record FailingTraceabilityStore(TraceabilityStore delegate) implements TraceabilityStore {
        private FailingTraceabilityStore {
            java.util.Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public void putLink(KnowledgeSnapshotId snapshotId, TraceabilityLink link) {
            delegate.putLink(snapshotId, link);
        }

        @Override
        public void putLinks(KnowledgeSnapshotId snapshotId, List<TraceabilityLink> links) {
            throw new KnowledgeStoreException("Injected M19 traceability persistence failure");
        }

        @Override
        public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId snapshotId, TraceabilityLinkId linkId) {
            return delegate.findLink(snapshotId, linkId);
        }

        @Override
        public List<TraceabilityLink> outgoing(
                KnowledgeSnapshotId snapshotId,
                TraceabilityEntityRef source,
                Set<TraceabilityRelationType> relationTypes) {
            return delegate.outgoing(snapshotId, source, relationTypes);
        }

        @Override
        public List<TraceabilityLink> incoming(
                KnowledgeSnapshotId snapshotId,
                TraceabilityEntityRef target,
                Set<TraceabilityRelationType> relationTypes) {
            return delegate.incoming(snapshotId, target, relationTypes);
        }
    }
}
