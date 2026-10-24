package com.morpheus.architecture.m19;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.ingestion.ProjectSnapshotImportResult;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingMode;
import com.morpheus.domain.constraint.ConstraintBlockingPolicy;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;
import com.morpheus.domain.evidence.Evidence;
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
import com.morpheus.store.sqlite.adapter.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.adapter.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
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
    private static final int CONTENT_READ_CHANGES = 2_000;
    private static final int CONTENT_READ_CRITERIA = 4_000;
    private static final long CONTENT_READ_BUDGET_NANOS = 1_000_000_000L;

    @TempDir
    Path tempDir;

    @Test
    void fiveLargePublishedSnapshotsStayWithinFrozenPublicationRetentionAndReopenBudgets() throws Exception {
        NormalizedProjectContent content = largeContent();

        // Warmup is isolated so the measured retention database still contains exactly five published snapshots.
        Path warmupDatabase = tempDir.resolve("full-publish-warmup.db");
        try (Stores warmup = Stores.open(warmupDatabase)) {
            ProjectSnapshotImportResult result = warmup.publisher().publishFull(
                    content, Optional.of("warmup"), T0.minusSeconds(1));
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
                        Optional.of("revision-large-%d".formatted(index)),
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
                databaseSizes.add(sqliteFootprint(database));
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

    /**
     * Reading back the business content of a large snapshot, in a database of its own.
     *
     * <p>Budget declared on 13/10/2026, before the read was changed (ADR-0085). The snapshot is
     * published into its own file so that neither the publication time, nor the database size, nor the retention
     * growth measured above moves. Every child table of the projection is filled: a list left empty measures
     * nothing, and the guards below refuse a fixture that leaves one.</p>
     */
    @Test
    void readingTheContentOfALargeSnapshotStaysWithinItsDeclaredBudget() throws Exception {
        NormalizedProjectContent content = contentWithEveryChildTableFilled();
        Path database = tempDir.resolve("content-read-large.db");

        try (Stores stores = Stores.open(database)) {
            ProjectSnapshotImportResult published = stores.publisher().publishFull(
                    content, Optional.of("revision-content-read"), T0);

            SnapshotBusinessContent warmUp =
                    stores.businessContent().findSnapshotContent(published.snapshot().id()).orElseThrow();
            assertEquals(CONTENT_READ_CHANGES, warmUp.changes().size());
            assertEquals(CONTENT_READ_CHANGES, warmUp.constraints().size());
            assertEquals(CONTENT_READ_CRITERIA, warmUp.acceptanceCriteria().size());
            assertEquals(CONTENT_READ_CRITERIA, warmUp.scenarios().size());
            assertTrue(warmUp.scenarios().stream().noneMatch(scenario -> scenario.preconditions().isEmpty()),
                    "a scenario without preconditions leaves a child table unmeasured");
            assertTrue(warmUp.changes().stream().noneMatch(change -> change.scope().isEmpty()
                            || change.outOfScope().isEmpty() || change.risks().isEmpty()),
                    "a change without scope, out-of-scope or risks leaves a child table unmeasured");
            assertTrue(warmUp.constraints().stream().noneMatch(constraint ->
                            constraint.blockingPolicy().targetStates().isEmpty()
                                    || constraint.supportingEvidenceIds().isEmpty()),
                    "a constraint without blocking targets or supporting evidence leaves a child table unmeasured");
            assertTrue(warmUp.acceptanceCriteria().stream()
                            .noneMatch(criterion -> criterion.verificationEvidenceIds().isEmpty()),
                    "a criterion without verification evidence leaves a child table unmeasured");

            List<Long> samples = new ArrayList<>(5);
            for (int index = 0; index < 5; index++) {
                long started = System.nanoTime();
                stores.businessContent().findSnapshotContent(published.snapshot().id()).orElseThrow();
                samples.add(System.nanoTime() - started);
            }
            long readP95 = M19LargeFixtureSupport.percentile95Nanos(samples);
            System.out.println("M19_METRIC snapshot_content_read_p95_ms=" + readP95 / 1_000_000L);
            assertTrue(readP95 <= CONTENT_READ_BUDGET_NANOS,
                    () -> "snapshot content read p95 exceeded its declared 1000ms budget: "
                            + readP95 / 1_000_000L + " ms");
        }
    }

    private NormalizedProjectContent contentWithEveryChildTableFilled() {
        ProviderId providerId = new ProviderId("m19-content-read");
        ProjectSpecificationId projectId =
                new ProjectSpecificationId(M19LargeFixtureSupport.deterministicIdentity(1951, 1));
        SpecificationId specificationId = new SpecificationId(M19LargeFixtureSupport.deterministicIdentity(1951, 2));
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(evidence(1951, 3, "specification.md"));
        Specification specification = new Specification(
                specificationId, projectId, "SPEC-M19-CONTENT", "M19 content read specification",
                Optional.of("Every child table of the content projection is filled"),
                provenance(providerId, 1951, 3, "specification.md", "SPEC-M19-CONTENT"));

        List<Requirement> requirements = new ArrayList<>(CONTENT_READ_CHANGES);
        List<ChangeProposal> changes = new ArrayList<>(CONTENT_READ_CHANGES);
        List<Constraint> constraints = new ArrayList<>(CONTENT_READ_CHANGES);
        for (int index = 0; index < CONTENT_READ_CHANGES; index++) {
            String requirementKey = "REQ-CR-%05d".formatted(index);
            requirements.add(new Requirement(
                    new RequirementId(M19LargeFixtureSupport.deterministicIdentity(1952, index)),
                    specificationId,
                    Optional.of(requirementKey),
                    "Requirement %05d".formatted(index),
                    "The content-read requirement %05d shall be read back as it was published.".formatted(index),
                    provenance(providerId, 1953, index, "requirements/%s.md".formatted(requirementKey),
                            requirementKey)));
            evidence.add(evidence(1953, index, "requirements/%s.md".formatted(requirementKey)));

            String changeKey = "CHG-CR-%05d".formatted(index);
            ChangeId changeId = new ChangeId(M19LargeFixtureSupport.deterministicIdentity(1954, index));
            changes.add(new ChangeProposal(
                    changeId,
                    projectId,
                    Optional.of(changeKey),
                    "Change %05d".formatted(index),
                    "Keep change %05d deterministic".formatted(index),
                    List.of("scope a of %05d".formatted(index), "scope b of %05d".formatted(index)),
                    List.of("out of scope of %05d".formatted(index)),
                    List.of("risk of %05d".formatted(index)),
                    provenance(providerId, 1955, index, "changes/%s.md".formatted(changeKey), changeKey)));
            Evidence changeEvidence = evidence(1955, index, "changes/%s.md".formatted(changeKey));
            evidence.add(changeEvidence);

            constraints.add(new Constraint(
                    new ConstraintId(M19LargeFixtureSupport.deterministicIdentity(1956, index)),
                    changeId,
                    "Change %05d must remain reviewable".formatted(index),
                    ConstraintApplicability.APPLICABLE,
                    ConstraintSeverity.ERROR,
                    ConstraintSatisfaction.UNKNOWN,
                    new ConstraintBlockingPolicy(
                            ConstraintBlockingMode.BLOCK_WHEN_VIOLATED,
                            List.of(ChangeLifecycleState.PROPOSED, ChangeLifecycleState.SPECIFIED)),
                    List.of(changeEvidence.id()),
                    provenance(providerId, 1955, index, "changes/%s.md".formatted(changeKey),
                            "CON-CR-%05d".formatted(index))));
        }

        List<Scenario> scenarios = new ArrayList<>(CONTENT_READ_CRITERIA);
        List<AcceptanceCriterion> criteria = new ArrayList<>(CONTENT_READ_CRITERIA);
        for (int index = 0; index < CONTENT_READ_CRITERIA; index++) {
            Requirement requirement = requirements.get(index % requirements.size());
            String scenarioSource = "scenarios/scenario-cr-%05d.md".formatted(index);
            scenarios.add(new Scenario(
                    new ScenarioId(M19LargeFixtureSupport.deterministicIdentity(1957, index)),
                    Optional.of(requirement.id()),
                    "Scenario %05d".formatted(index),
                    List.of("the content-read fixture is loaded", "scenario %05d is selected".formatted(index)),
                    "the scenario %05d is evaluated".formatted(index),
                    "requirement %s remains traceable".formatted(requirement.key().orElseThrow()),
                    provenance(providerId, 1958, index, scenarioSource, "SCN-CR-%05d".formatted(index))));
            Evidence scenarioEvidence = evidence(1958, index, scenarioSource);
            evidence.add(scenarioEvidence);

            criteria.add(new AcceptanceCriterion(
                    new AcceptanceCriterionId(M19LargeFixtureSupport.deterministicIdentity(1959, index)),
                    Optional.of(requirement.id()),
                    Optional.of(changes.get(index % changes.size()).id()),
                    "Criterion %05d".formatted(index),
                    "scenario %05d passes".formatted(index),
                    VerificationStatus.VERIFIED,
                    List.of(scenarioEvidence.id()),
                    provenance(providerId, 1958, index, scenarioSource, "AC-CR-%05d".formatted(index))));
        }

        return new NormalizedProjectContent(
                new ProjectSpecification(projectId, "M19 content read fixture", SourceLocator.file("m19/content-read")),
                List.of(specification),
                requirements,
                scenarios,
                changes,
                List.of(),
                constraints,
                List.of(),
                List.of(),
                criteria,
                evidence,
                List.of());
    }

    private void assertCounts(ProjectSnapshotImportResult result, Stores stores) {
        assertEquals(M19LargeFixtureSupport.GATE_REQUIREMENTS, result.requirementCount());
        assertEquals(M19LargeFixtureSupport.GATE_TRACEABILITY_LINKS, result.traceabilityLinkCount());
        assertEquals(M19LargeFixtureSupport.GATE_REQUIREMENTS,
                stores.requirements().listRequirementVersions(result.snapshot().id()).size());
        assertEquals(M19LargeFixtureSupport.GATE_TRACEABILITY_LINKS,
                persistedTraceabilityLinkCount(stores.database(), result.snapshot().id()));
    }

    private long persistedTraceabilityLinkCount(
            Path database,
            com.morpheus.domain.snapshot.KnowledgeSnapshotId snapshotId) {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath().normalize());
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM snapshot_traceability_links WHERE snapshot_id = ?")) {
            statement.setString(1, snapshotId.toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("Cannot count persisted M19 traceability links", failure);
        }
    }

    private long sqliteFootprint(Path database) throws java.io.IOException {
        long total = 0L;
        for (String suffix : List.of("", "-journal", "-wal", "-shm")) {
            Path entry = Path.of(database + suffix);
            if (Files.isRegularFile(entry)) {
                total += Files.size(entry);
            }
        }
        return total;
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
        List<Evidence> evidence = new ArrayList<>(M19LargeFixtureSupport.GATE_TRACEABILITY_LINKS + 1);
        evidence.add(evidence(1941, 1, "specification.md"));
        for (int index = 0; index < M19LargeFixtureSupport.GATE_REQUIREMENTS; index++) {
            String key = "REQ-%05d".formatted(index);
            requirements.add(new Requirement(
                    new RequirementId(M19LargeFixtureSupport.deterministicIdentity(1942, index)),
                    specificationId,
                    Optional.of(key),
                    "Requirement %05d".formatted(index),
                    "The M19 full-publish requirement %05d shall remain deterministic at scale.".formatted(index),
                    provenance(providerId, 1943, index, "requirements/%s.md".formatted(key), key)));
            evidence.add(evidence(1943, index, "requirements/%s.md".formatted(key)));
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
                    "requirement %s remains traceable".formatted(requirement.key().orElseThrow()),
                    provenance(providerId, 1945, index, "scenarios/scenario-%05d.md".formatted(index), "SCN-%05d".formatted(index))));
            evidence.add(evidence(1945, index, "scenarios/scenario-%05d.md".formatted(index)));
        }

        return new NormalizedProjectContent(
                project,
                List.of(specification),
                requirements,
                scenarios,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                evidence,
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
                Optional.of("revision-full-publish"),
                new EvidenceId(M19LargeFixtureSupport.deterministicIdentity(namespace, ordinal)));
    }

    private record Stores(
            Path database,
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
            return new Stores(database, snapshots, requirements, business, traceability, publisher);
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
