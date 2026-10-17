package com.morpheus.store.sqlite;

import com.morpheus.application.policy.PolicyAuditPage;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.query.PageRequest;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A page of the policy audit is read at the store (AUD-PRF-04): the database follows an index in the order the journal
 * is read in, and the store builds only the records of the page.
 */
class SqlitePolicyAuditPageTest {
    private static final int RECORDS = 130;

    @TempDir
    Path tempDir;

    @Test
    void aPageHoldsOnlyItsRecordsAndSaysHowManyFollow() {
        Path database = tempDir.resolve("page.db");
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(database)) {
            PolicyIds.PackId packId = journal(store, RECORDS);

            PolicyAuditPage first = store.listAudit(packId, new PageRequest(0, 10));
            PolicyAuditPage last = store.listAudit(packId, new PageRequest(125, 10));
            PolicyAuditPage beyond = store.listAudit(packId, new PageRequest(500, 10));

            assertEquals(10, first.items().size());
            assertEquals(RECORDS, first.totalMatches());
            assertTrue(first.hasMore());
            assertEquals(5, last.items().size(), "the last page holds what is left");
            assertFalse(last.hasMore());
            assertEquals(List.of(), beyond.items(), "a page past the end is empty, not an error");
            assertEquals(RECORDS, beyond.totalMatches());
            assertFalse(beyond.hasMore());
        }
    }

    @Test
    void pagesConcatenateToTheNaturalOrderAcrossEveryBoundary() {
        Path database = tempDir.resolve("concatenation.db");
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(database)) {
            PolicyIds.PackId packId = journal(store, RECORDS);
            List<PolicyConfiguration.AuditRecord> concatenated = new ArrayList<>();
            for (int offset = 0; offset < RECORDS; offset += 7) {
                concatenated.addAll(store.listAudit(packId, new PageRequest(offset, 7)).items());
            }

            assertEquals(RECORDS, concatenated.size());
            assertEquals(concatenated.stream().sorted().toList(), concatenated,
                    "the database order is the natural order of the records");
            assertEquals(RECORDS, concatenated.stream().map(PolicyConfiguration.AuditRecord::id).distinct().count(),
                    "no record is read twice or skipped at a boundary");
        }
    }

    @Test
    void theQueryFollowsTheOrderIndexAndSortsNothing() throws Exception {
        Path database = tempDir.resolve("plan.db");
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(database)) {
            journal(store, 3);
        }
        String plan = queryPlan(database);

        assertTrue(plan.contains("idx_policy_audit_pack_order"), plan);
        assertFalse(plan.contains("TEMP B-TREE"), "the database must not sort what the index already orders: " + plan);
    }

    @Test
    void theTextOfTheInstantDoesNotGiveTheOrderTheKeyDoes() throws Exception {
        Path database = tempDir.resolve("text-order.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE audit(id TEXT, at TEXT)");
            statement.execute("INSERT INTO audit VALUES ('a', '2026-10-09T14:30:00Z')");
            statement.execute("INSERT INTO audit VALUES ('b', '2026-10-09T14:30:00.500Z')");
            statement.execute("INSERT INTO audit VALUES ('c', '2026-10-09T14:30:00.500100Z')");
            StringBuilder byText = new StringBuilder();
            try (ResultSet rows = statement.executeQuery("SELECT id FROM audit ORDER BY at, id")) {
                while (rows.next()) {
                    byText.append(rows.getString(1));
                }
            }
            assertEquals("cba", byText.toString(),
                    "ordered by the text of the instant the earliest record comes last: the key exists for this");
        }
    }

    private String queryPlan(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "EXPLAIN QUERY PLAN " + SqlitePolicyPackStore.AUDIT_PAGE_SQL.replace("?", "'p'"))) {
            StringBuilder plan = new StringBuilder();
            while (rows.next()) {
                plan.append(rows.getString("detail")).append('\n');
            }
            return plan.toString();
        }
    }

    private static PolicyIds.PackId journal(SqlitePolicyPackStore store, int records) {
        PolicyPackService service = new PolicyPackService(store, Clock.systemUTC());
        var definition = service.create("Governance", List.of(rule()), "alice", "write-0");
        for (int index = 1; index < records; index++) {
            service.update(definition.id(), index, "Governance", List.of(rule()), "alice", "write-" + index);
        }
        return definition.id();
    }

    private static PolicyRule rule() {
        return new PolicyRule(
                PolicyIds.RuleId.generate(),
                "Lifecycle guard",
                PolicyRule.Kind.LIFECYCLE_GUARD,
                PolicyRule.Severity.WARNING,
                new PolicyRule.LifecycleGuard(
                        ChangeId.generate(), ChangeLifecycleState.PROPOSED, ChangeLifecycleState.SPECIFIED));
    }
}
