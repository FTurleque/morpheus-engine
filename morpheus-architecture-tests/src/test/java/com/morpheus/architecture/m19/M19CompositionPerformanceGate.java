package com.morpheus.architecture.m19;

import com.morpheus.application.composition.CompositionQueryService;
import com.morpheus.application.composition.CompositionSnapshotState;
import com.morpheus.application.composition.MultiProviderCompositionResult;
import com.morpheus.application.composition.MultiProviderCompositionService;
import com.morpheus.application.composition.ProviderContribution;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
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
import com.morpheus.store.sqlite.SqliteCompositionStateStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit M19 multi-provider scale gate; invoked only by validate-m19. */
class M19CompositionPerformanceGate {
    private static final int REQUIREMENTS_PER_PROVIDER = 5_000;
    private static final int SHARED_CONFLICTING_REQUIREMENTS = 1_000;
    private static final long QUERY_BUDGET_NANOS = 1_000_000_000L;
    private static final int WARMUP_ITERATIONS = 1;
    private static final int MEASURED_ITERATIONS = 5;
    private static final Instant T0 = Instant.parse("2026-07-26T18:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void tenThousandObservationsAndOneThousandConflictsRemainQueryableWithinFrozenBudget() {
        ProjectSpecificationId projectId = new ProjectSpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1930, 1));
        ProviderContribution primary = contribution(
                projectId,
                new ProviderId("m19-primary"),
                100,
                1931,
                false);
        ProviderContribution secondary = contribution(
                projectId,
                new ProviderId("m19-secondary"),
                50,
                1932,
                true);
        MultiProviderCompositionService composer = new MultiProviderCompositionService();

        MultiProviderCompositionResult expected = composer.compose(List.of(primary, secondary));
        assertEquals(REQUIREMENTS_PER_PROVIDER * 2, expected.content().requirements().size());
        assertEquals(SHARED_CONFLICTING_REQUIREMENTS, expected.conflicts().size());

        List<Long> compositionSamples = new ArrayList<>(MEASURED_ITERATIONS);
        for (int index = 0; index < MEASURED_ITERATIONS; index++) {
            long started = System.nanoTime();
            MultiProviderCompositionResult actual = composer.compose(List.of(primary, secondary));
            compositionSamples.add(System.nanoTime() - started);
            assertEquals(expected.content().requirements().size(), actual.content().requirements().size());
            assertEquals(expected.conflicts(), actual.conflicts());
        }
        long compositionP95 = M19LargeFixtureSupport.percentile95Nanos(compositionSamples);
        System.out.println("M19_METRIC composition_build_p95_ms=" + compositionP95 / 1_000_000L);

        Path database = tempDir.resolve("composition-large.db");
        KnowledgeSnapshotId snapshotId = new KnowledgeSnapshotId(
                M19LargeFixtureSupport.deterministicIdentity(1930, 2));
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var compositionState = new SqliteCompositionStateStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("m19/composition-large")));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId,
                    projectId,
                    Optional.empty(),
                    KnowledgeSnapshotState.READY,
                    Optional.of("revision-composition-large"),
                    T0));
            snapshots.activateSnapshot(snapshotId, Optional.empty());
            compositionState.save(CompositionSnapshotState.from(snapshotId, expected));

            CompositionQueryService queries = new CompositionQueryService(snapshots, compositionState);
            var expectedStatus = queries.findActive(projectId).orElseThrow();
            var expectedConflicts = expectedStatus.conflicts();
            assertEquals(SHARED_CONFLICTING_REQUIREMENTS, expectedStatus.conflicts().size());
            assertEquals(SHARED_CONFLICTING_REQUIREMENTS, expectedConflicts.size());

            for (int index = 0; index < WARMUP_ITERATIONS; index++) {
                assertEquals(expectedStatus, queries.findActive(projectId).orElseThrow());
                assertEquals(expectedConflicts, queries.findActive(projectId).orElseThrow().conflicts());
            }

            List<Long> statusSamples = new ArrayList<>(MEASURED_ITERATIONS);
            List<Long> conflictSamples = new ArrayList<>(MEASURED_ITERATIONS);
            for (int index = 0; index < MEASURED_ITERATIONS; index++) {
                long statusStarted = System.nanoTime();
                assertEquals(expectedStatus, queries.findActive(projectId).orElseThrow());
                statusSamples.add(System.nanoTime() - statusStarted);

                long conflictsStarted = System.nanoTime();
                assertEquals(expectedConflicts, queries.findActive(projectId).orElseThrow().conflicts());
                conflictSamples.add(System.nanoTime() - conflictsStarted);
            }

            long statusP95 = M19LargeFixtureSupport.percentile95Nanos(statusSamples);
            long conflictsP95 = M19LargeFixtureSupport.percentile95Nanos(conflictSamples);
            System.out.println("M19_METRIC composition_status_p95_ms=" + statusP95 / 1_000_000L);
            System.out.println("M19_METRIC composition_conflicts_p95_ms=" + conflictsP95 / 1_000_000L);
            assertTrue(statusP95 <= QUERY_BUDGET_NANOS,
                    () -> "composition status p95 exceeded frozen 1000ms budget: " + statusP95 / 1_000_000L + " ms");
            assertTrue(conflictsP95 <= QUERY_BUDGET_NANOS,
                    () -> "composition conflicts p95 exceeded frozen 1000ms budget: " + conflictsP95 / 1_000_000L + " ms");
        }
    }

    private ProviderContribution contribution(
            ProjectSpecificationId projectId,
            ProviderId providerId,
            int priority,
            long namespace,
            boolean secondary) {
        ProjectSpecification project = new ProjectSpecification(
                projectId,
                "M19 composition fixture",
                SourceLocator.file("m19/composition-large"));
        SpecificationId specificationId = new SpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(namespace, 1));
        Specification specification = new Specification(
                specificationId,
                projectId,
                "SPEC-M19",
                "M19 specification",
                Optional.of("Deterministic large multi-provider fixture"),
                provenance(providerId, namespace, 1, "specification.md", "SPEC-M19"));

        List<Requirement> requirements = new ArrayList<>(REQUIREMENTS_PER_PROVIDER);
        List<Evidence> evidence = new ArrayList<>(REQUIREMENTS_PER_PROVIDER + 1);
        evidence.add(evidence(namespace, 1, "specification.md"));
        for (int index = 0; index < REQUIREMENTS_PER_PROVIDER; index++) {
            boolean shared = index < SHARED_CONFLICTING_REQUIREMENTS;
            String key = secondary && !shared
                    ? "B-REQ-%05d".formatted(index)
                    : "REQ-%05d".formatted(index);
            String statement = shared && secondary
                    ? "Secondary conflicting statement for %s".formatted(key)
                    : "Canonical statement for %s".formatted(key);
            requirements.add(new Requirement(
                    new RequirementId(M19LargeFixtureSupport.deterministicIdentity(namespace, 10_000L + index)),
                    specificationId,
                    Optional.of(key),
                    "Requirement %05d".formatted(index),
                    statement,
                    provenance(providerId, namespace, 20_000L + index, "requirements/%s.md".formatted(key), key)));
            evidence.add(evidence(namespace, 20_000L + index, "requirements/%s.md".formatted(key)));
        }

        NormalizedProjectContent content = new NormalizedProjectContent(
                project,
                List.of(specification),
                requirements,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                evidence,
                List.of());
        return new ProviderContribution(
                providerId,
                priority,
                true,
                new ProviderReadResult(providerId, Optional.of(content), List.of(), List.of()));
    }

    private Evidence evidence(long namespace, long ordinal, String source) {
        return new Evidence(
                new EvidenceId(M19LargeFixtureSupport.deterministicIdentity(namespace + 100, ordinal)),
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
                Optional.of("revision-composition-large"),
                new EvidenceId(M19LargeFixtureSupport.deterministicIdentity(namespace + 100, ordinal)));
    }
}
