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
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit M19 full-publish, retention and SQLite-reopen gate; invoked only by validate-m19. */
class M19FullPublishPerformanceGate {
    private static final int SCENARIOS = 15_000;
    private static final long PUBLISH_BUDGET_NANOS = 60_000_000_000L;
    private static final long REOPEN_BUDGET_NANOS = 2_000_000_000L;
    private static final long SQLITE_SIZE_BUDGET_BYTES = 512L * 1024L * 1024L;
    private static final long RETENTION_GROWTH_BUDGET_BYTES = 128L * 1024L * 1024L;
    private static final Instant T0 = Instant.parse("2026-07-26T18:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void fiveLargePublishedSnapshotsStayWithinFrozenPublicationRetentionAndReopenBudgets() throws Exception {
        NormalizedProjectContent content = largeContent();

        // Warmup is isolated so the measured retention database still contains exactly five published snapshots.
        Path warmupDatabase = tempDir.resolve("full-publish-warmup.db");
        try (Stores warmup = Stores.open(warmupDatabase)) {
            ProjectSnapshotImportResult result = warmup.publisher().publishFull(content, "warmup", T0.minusSeconds(1));
            assertCounts(result, warmup);
        }

        Path database = tempDir.resolve("full-publish-large.db");
        List<Long> publishSamples = new ArrayList<>(M19LargeFixtureSupport.GATE_RETAINED_SNAPSHOTS);
        List<Long> databaseSizes = new ArrayList<>(M19LargeFixtureSupport.GATE_RETAINED_SNAPSHOTS);
        ProjectSpecificationId projectId = content.project().id();

        try (Stores stores = Stores.open(database)) {
            for (int index = 0; index < M19LargeFixtureSupport.GATE_RETAINED_SNAPSHOTS; index++) {
                long started = System.nanoTime();
                ProjectSnapshotImportResult result = stores.publisher().publishFull(
                        content,
                        "revision-large-%d".formatted(index),
                        T0.plusSeconds(index));
                publishSamples.add(System.nanoTime() - started);

                assertCounts(result, stores);
                assertEquals(result.snapshot().id(), stores.snapshots().activeSnapshot(projectId).orElseThrow().id());
                assertEquals(1L, stores.snapshots().listSnapshots(projectId).stream()
                        .filter(snapshot -> snapshot.state() == com.morpheus.domain.snapshot.KnowledgeSnapshotState.ACTIVE)
                        .count());
                assertEquals(index, stores.snapshots().listSnapshots(projectId).stream()
                        .filter(snapshot -> snapshot.state() == com.morpheus.domain.snapshot.KnowledgeSnapshotState.RETIRED)
                        .count());
                databaseSizes.add(Files.size(database));
                System.out.println("M19_METRIC sqlite_size_after_snapshot_" + (index + 1) + "_bytes="
                        + databaseSizes.getLast());
            }
        }

        long publishP95 = M19LargeFixtureSupport.percentile95Nanos(publishSamples);
        long finalSize = databaseSizes.getLast();
        long retentionGrowth = finalSize - databaseSizes.get(databaseSizes.size() - 2);
        System.out.println("M19_METRIC full_publish_p95_ms=" + publishP95 / 1_000_000L);
        System.out.println("M19_METRIC sqlite_final_bytes=" + finalSize);
        System.out.println("M19_METRIC sqlite_retention_growth_bytes=" + retentionGrowth);
        assertTrue(publishP95 <= PUBLISH_BUDGET_NANOS,
                () -> "full publish p95 exceeded frozen 60s budget: " + publishP95 / 1_000_000L + " ms");
        assertTrue(finalSize <= SQLITE_SIZE_BUDGET_BYTES,
                () -> "five-snapshot SQLite size exceeded frozen 512 MiB budget: " + finalSize);
        assertTrue(retentionGrowth <= RETENTION_GROWTH_BUDGET_BYTES,
                () -> "single-snapshot retention growth exceeded frozen 128 MiB budget: " + retentionGrowth);

        try (SqliteSpecificationKnowledgeStore warmReopen = new SqliteSpecificationKnowledgeStore(database)) {
            assertTrue(warmReopen.activeSnapshot(projectId).isPresent());
        }
        List<Long> reopenSamples = new ArrayList<>(5);
        for (int index = 0; index < 5; index++) {
            long started = System.nanoTime();
            try (SqliteSpecificationKnowledgeStore reopened = new SqliteSpecificationKnowledgeStore(database)) {
                assertTrue(reopened.activeSnapshot(projectId).isPresent());
            }
            reopenSamples.add(System.nanoTime() - started);
        }
        long reopenP95 = M19LargeFixtureSupport.percentile95Nanos(reopenSamples);
        System.out.println("M19_METRIC sqlite_reopen_p95_ms=" + reopenP95 / 1_000_000L);
        assertTrue(reopenP95 <= REOPEN_BUDGET_NANOS,
                () -> "SQLite reopen p95 exceeded frozen 2000ms budget: " + reopenP95 / 1_000_000L + " ms");
    }

    private void assertCounts(ProjectSnapshotImportResult result, Stores stores) {
        assertEquals(M19LargeFixtureSupport.GATE_REQUIREMENTS, result.requirementCount());
        assertEquals(M19LargeFixtureSupport.GATE_TRACEABILITY_LINKS, result.traceabilityLinkCount());
        assertEquals(M19LargeFixtureSupport.GATE_REQUIREMENTS,
                stores.requirements().listRequirementVersions(result.snapshot().id()).size());
        assertEquals(M19LargeFixtureSupport.GATE_TRACEABILITY_LINKS,
                stores.traceability().listLinks(result.snapshot().id()).size());
    }

    private NormalizedProjectContent largeContent() {
        ProviderId providerId = new ProviderId("m19-full-publish");
        ProjectSpecificationId projectId = new ProjectSpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1940, 1));
        SpecificationId specificationId = new SpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1940, 2));
        ProjectSpecification project = new ProjectSpecification(
                projectId,
                "M19 full publish fixture",
                SourceLocator.file("m19/full-publish"));
        Specification specification = new Specification(
                specificationId,
                projectId,
                "SPEC-M19-PUBLISH",
                "M19 full publication specification",
                Optional.of("Large deterministic publication fixture"),
                provenance(providerId, 1941, 1, "specification.md", "SPEC-M19-PUBLISH"));

        List<Requirement> requirements = new ArrayList<>(M19LargeFixtureSupport.GATE_REQUIREMENTS);
        for (int index = 0; index < M19LargeFixtureSupport.GATE_REQUIREMENTS; index++) {
            String key = "REQ-%05d".formatted(index);
            requirements.add(new Requirement(
                    new RequirementId(M19LargeFixtureSupport.deterministicIdentity(1942, index)),
                    specificationId,
                    Optional.of(key),
                    "Requirement %05d".formatted(index),
                    "The M19 full-publish requirement %05d shall remain deterministic at scale.".formatted(index),
                    provenance(providerId, 1943, index, "requirements/%s.md".formatted(key), key)));
        }

        List<Scenario> scenarios = new ArrayList<>(SCENARIOS);
        for (int index = 0; index < SCENARIOS; index++) {
            Requirement requirement = requirements.get(index % requirements.size());
            scenarios.add(new Scenario(
                    new ScenarioId(M19LargeFixtureSupport.deterministicIdentity(1944, index)),
                    Optional.of(requirement.id()),
                    "Scenario %05d".formatted(index),
                    List.of("the deterministic M19 fixture is loaded"),
                    "the scenario %05d is evaluated".formatted(index),
                    "requirement %s remains traceable".formatted(requirement.externalKey().orElseThrow()),
                    provenance(providerId, 1945, index, "scenarios/scenario-%05d.md".formatted(index), "SCN-%05d".formatted(index))));
        }

        return new NormalizedProjectContent(
                project,
                List.of(specification),
                requirements,
                List.of(),
                scenarios,
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
                Optional.of("revision-full-publish"),
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
