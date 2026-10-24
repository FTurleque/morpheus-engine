package com.morpheus.architecture.m19;

import com.morpheus.application.policy.PolicyAuditPage;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.store.sqlite.adapter.SqlitePolicyPackStore;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.RequirementQueryService;
import com.morpheus.application.query.RequirementSearchPage;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
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
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit M19 requirement-query gate; invoked only by validate-m19. */
class M19QueryPerformanceGate {
    private static final long QUERY_BUDGET_NANOS = 1_000_000_000L;
    private static final int WARMUP_ITERATIONS = 1;
    private static final int MEASURED_ITERATIONS = 5;
    private static final Instant T0 = Instant.parse("2026-07-26T18:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void tenThousandRequirementSqliteQueryStaysWithinFrozenBudgetAndOrderingIsStable() {
        Path database = tempDir.resolve("requirements-large.db");
        ProjectSpecificationId projectId = new ProjectSpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1910, 1));
        SpecificationId specificationId = new SpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1910, 2));
        SpecificationVersionId versionId = new SpecificationVersionId(
                M19LargeFixtureSupport.deterministicIdentity(1910, 3));
        KnowledgeSnapshotId snapshotId = new KnowledgeSnapshotId(
                M19LargeFixtureSupport.deterministicIdentity(1910, 4));

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("m19/query-large")));
            requirements.putSpecificationVersion(new SpecificationVersion(
                    versionId,
                    projectId,
                    Optional.of(1L),
                    Optional.of("m19-query"),
                    Optional.of("revision-query-large"),
                    T0,
                    Optional.empty()));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId,
                    projectId,
                    Optional.empty(),
                    KnowledgeSnapshotState.READY,
                    Optional.of("revision-query-large"),
                    T0));
            requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
            for (int index = 0; index < M19LargeFixtureSupport.GATE_REQUIREMENTS; index++) {
                requirements.putRequirementVersion(requirementRecord(
                        snapshotId,
                        versionId,
                        specificationId,
                        index));
            }
            snapshots.activateSnapshot(snapshotId, Optional.empty());

            RequirementQueryService service = new RequirementQueryService(snapshots, requirements);
            RequirementSearchQuery query = new RequirementSearchQuery("common-marker");
            PageRequest page = new PageRequest(0, 50);
            List<RequirementId> expectedOrder = service.findActive(projectId, query, page)
                    .orElseThrow()
                    .items().stream()
                    .map(record -> record.entityVersion().content().id())
                    .toList();

            for (int index = 0; index < WARMUP_ITERATIONS; index++) {
                assertPage(service.findActive(projectId, query, page).orElseThrow(), expectedOrder);
            }

            List<Long> samples = new ArrayList<>(MEASURED_ITERATIONS);
            for (int index = 0; index < MEASURED_ITERATIONS; index++) {
                long started = System.nanoTime();
                RequirementSearchPage result = service.findActive(projectId, query, page).orElseThrow();
                samples.add(System.nanoTime() - started);
                assertPage(result, expectedOrder);
            }

            long p95 = M19LargeFixtureSupport.percentile95Nanos(samples);
            System.out.println("M19_METRIC requirement_query_p95_ms=" + p95 / 1_000_000L);
            assertTrue(p95 <= QUERY_BUDGET_NANOS,
                    () -> "requirement query p95 exceeded frozen 1000ms budget: " + p95 / 1_000_000L + " ms");
        }
    }

    private RequirementVersionRecord requirementRecord(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            SpecificationId specificationId,
            int index) {
        RequirementId requirementId = new RequirementId(
                M19LargeFixtureSupport.deterministicIdentity(1911, index));
        EntityVersionId entityVersionId = new EntityVersionId(
                M19LargeFixtureSupport.deterministicIdentity(1912, index));
        EvidenceId evidenceId = new EvidenceId(
                M19LargeFixtureSupport.deterministicIdentity(1913, index));
        String key = "REQ-%05d".formatted(index);
        Requirement requirement = new Requirement(
                requirementId,
                specificationId,
                Optional.of(key),
                "Requirement %05d common-marker".formatted(index),
                "The M19 common-marker requirement %05d shall remain deterministic and queryable at scale."
                        .formatted(index),
                new Provenance(
                        new ProviderId("m19-large"),
                        Optional.of("1"),
                        SourceLocator.file("specs/requirement-%05d.md".formatted(index)),
                        Optional.of(key),
                        Optional.of("revision-query-large"),
                        evidenceId));
        return new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        entityVersionId,
                        requirementId.value(),
                        versionId,
                        TemporalState.CURRENT,
                        requirement));
    }

    /*
     * Budget declared on 13/10/2026 (M19_PERFORMANCE_BUDGETS.md, section 4 bis) before the policy audit was read by
     * pages: one page of 50 entries in a journal of 100,000, an interactive read, judged against the budget of the
     * requirement search. The journal is generated by one statement, with the order key written by the same expression
     * migration 21 uses, over the three spellings of an instant that make the text of the instant sort wrongly.
     */
    private static final int JOURNAL_RECORDS = 100_000;
    private static final int JOURNAL_PAGE = 50;

    @Test
    void aPageOfThePolicyAuditInAJournalOfOneHundredThousandStaysWithinBudget() throws Exception {
        Path database = tempDir.resolve("policy-audit-large.db");
        PolicyIds.PackId packId;
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(database)) {
            packId = new PolicyPackService(store, Clock.fixed(T0, ZoneOffset.UTC)).create(
                    "Large journal",
                    List.of(new PolicyRule(
                            PolicyIds.RuleId.generate(), "Lifecycle guard", PolicyRule.Kind.LIFECYCLE_GUARD,
                            PolicyRule.Severity.WARNING,
                            new PolicyRule.LifecycleGuard(
                                    ChangeId.generate(), ChangeLifecycleState.PROPOSED, ChangeLifecycleState.SPECIFIED))),
                    "m19", "journal").id();
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             PreparedStatement fill = connection.prepareStatement("""
                     WITH RECURSIVE n(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < ?),
                          t(i, secs, variant) AS (SELECT i, 1790000000 + i / 3, i % 3 FROM n)
                     INSERT INTO policy_audit(id, action, pack_id, actor, reason, at, at_key)
                     SELECT printf('018f0000-0000-7000-8000-%012d', i), 'UPDATE', ?, 'm19', 'journal',
                            strftime('%Y-%m-%dT%H:%M:%S', secs, 'unixepoch') ||
                                CASE variant WHEN 0 THEN 'Z' WHEN 1 THEN '.500Z' ELSE '.500100Z' END,
                            printf('%013d', secs) || '.' ||
                                CASE variant WHEN 0 THEN '000000000' WHEN 1 THEN '500000000' ELSE '500100000' END
                     FROM t
                     """)) {
            fill.setInt(1, JOURNAL_RECORDS - 1);
            fill.setString(2, packId.toString());
            fill.executeUpdate();
        }

        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(database)) {
            PolicyAuditPage deepest = store.listAudit(
                    packId, new PageRequest(JOURNAL_RECORDS - JOURNAL_PAGE, JOURNAL_PAGE));
            // The creation record is one more row: the journal holds the generated ones and it.
            assertEquals(JOURNAL_RECORDS + 1, deepest.totalMatches());
            assertEquals(JOURNAL_PAGE, deepest.items().size());
            assertEquals(deepest.items().stream().sorted().toList(), deepest.items(), "the page is in natural order");

            List<Long> samples = new ArrayList<>(MEASURED_ITERATIONS);
            for (int index = 0; index < WARMUP_ITERATIONS; index++) {
                store.listAudit(packId, new PageRequest(JOURNAL_RECORDS - JOURNAL_PAGE, JOURNAL_PAGE));
            }
            for (int index = 0; index < MEASURED_ITERATIONS; index++) {
                long started = System.nanoTime();
                PolicyAuditPage page = store.listAudit(
                        packId, new PageRequest(JOURNAL_RECORDS - JOURNAL_PAGE, JOURNAL_PAGE));
                samples.add(System.nanoTime() - started);
                assertEquals(deepest, page);
            }
            long p95 = M19LargeFixtureSupport.percentile95Nanos(samples);
            System.out.println("M19_METRIC policy_audit_page_p95_ms=" + p95 / 1_000_000L);
            System.out.println("M19_METRIC policy_audit_journal_records=" + deepest.totalMatches());
            assertTrue(p95 <= QUERY_BUDGET_NANOS,
                    () -> "policy audit page p95 exceeded declared 1000ms budget: " + p95 / 1_000_000L + " ms");
        }
    }

    private void assertPage(RequirementSearchPage result, List<RequirementId> expectedOrder) {
        assertEquals(M19LargeFixtureSupport.GATE_REQUIREMENTS, result.totalMatches());
        assertEquals(50, result.items().size());
        assertTrue(result.hasMore());
        assertEquals(expectedOrder, result.items().stream()
                .map(record -> record.entityVersion().content().id())
                .toList());
    }
}
