package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs {@code EXPLAIN QUERY PLAN}, through the driver the product ships, on the text of every guarded read statement
 * and refuses a plan that scans a table of the schema or sorts in a temporary b-tree, unless the statement is listed
 * with the reason it may. The plans are judged by shape, never compared with a recorded text: a driver bump that
 * rewords a line must not fail the build, one that brings a sort back must.
 */
class SqliteQueryPlanGuardTest {

    /**
     * A guarded statement. {@code excuse} is the reason its plan may scan or sort, or null when it may not;
     * {@code searchTerm} is a fragment some line of the plan must carry, or null when none is required.
     */
    record Guarded(String name, String sql, String excuse, String searchTerm) {
        Guarded {
            if (excuse != null && excuse.isBlank()) {
                throw new IllegalArgumentException("guarded statement " + name + " is excused without a reason");
            }
        }

        Guarded(String name, String sql, String excuse) {
            this(name, sql, excuse, null);
        }

        static Guarded served(String name, String sql) {
            return new Guarded(name, sql, null, null);
        }

        static Guarded searching(String name, String sql, String searchTerm) {
            return new Guarded(name, sql, null, searchTerm);
        }
    }

    private static final String ENDPOINT_GROUPED_BY_TYPE =
            "the endpoint index returns links grouped by relation type, so the order by link id sorts the links of one "
                    + "endpoint kept by the filter";

    private static final List<Guarded> GUARDED = List.of(
            Guarded.served("requirement-versions.current", SqliteVersionedRequirementStore.LIST_CURRENT_SQL),
            Guarded.served("requirement-versions.current-bounded", SqliteVersionedRequirementStore.LIST_CURRENT_BOUNDED_SQL),
            Guarded.served("saved-views.list-by-scope", SqliteSavedViewStore.LIST_BY_SCOPE_SQL),
            Guarded.served("saved-views.insert-within-budget", SqliteSavedViewStore.INSERT_WITHIN_BUDGET_SQL),
            Guarded.served("saved-view-versions.list", SqliteSavedViewStore.LIST_VERSIONS_SQL),
            Guarded.served("policy-pack-activations.list", SqlitePolicyPackStore.LIST_ACTIVATIONS_SQL),
            Guarded.served("policy-overrides.list", SqlitePolicyPackStore.LIST_OVERRIDES_SQL),
            Guarded.served("sync-inventory-entries.read", SqliteSyncStateStore.READ_INVENTORY_ENTRIES_SQL),
            Guarded.served("sync-source-archives.list", SqliteSyncStateStore.LIST_ARCHIVES_SQL),
            new Guarded("traceability.outgoing-unfiltered", SqliteTraceabilityStore.endpointSql(true, 0),
                    ENDPOINT_GROUPED_BY_TYPE, "source_identity_id=?"),
            Guarded.searching("traceability.outgoing-one-type",
                    SqliteTraceabilityStore.endpointSql(true, 1), "relation_type=?"),
            new Guarded("traceability.outgoing-several-types", SqliteTraceabilityStore.endpointSql(true, 3),
                    ENDPOINT_GROUPED_BY_TYPE, "relation_type=?"),
            Guarded.searching("traceability.incoming-one-type",
                    SqliteTraceabilityStore.endpointSql(false, 1), "relation_type=?"),
            new Guarded("traceability.incoming-several-types", SqliteTraceabilityStore.endpointSql(false, 3),
                    ENDPOINT_GROUPED_BY_TYPE, "relation_type=?"));

    @TempDir
    Path tempDir;

    @Test
    void everyGuardedStatementIsServedByAnIndexOnAMigratedDatabase() throws Exception {
        Path database = tempDir.resolve("migrated.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // The constructor applies every migration.
        }

        List<String> problems = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            for (Guarded guarded : GUARDED) {
                problems.addAll(problems(connection, guarded));
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void aStatementThatScansATableIsReportedWithItsPlanLine() throws Exception {
        try (Connection connection = scratch()) {
            List<String> problems = problems(connection,
                    Guarded.served("items.by-owner", "SELECT * FROM items WHERE label = ?"));

            assertEquals(1, problems.size(), problems.toString());
            assertTrue(problems.getFirst().startsWith("items.by-owner: SCAN items"), problems.getFirst());
        }
    }

    @Test
    void aStatementThatSortsInATemporaryTreeIsReportedWithItsPlanLine() throws Exception {
        try (Connection connection = scratch()) {
            List<String> problems = problems(connection,
                    Guarded.served("items.by-owner-ordered", "SELECT * FROM items WHERE owner = ? ORDER BY label COLLATE NOCASE"));

            assertEquals(1, problems.size(), problems.toString());
            assertTrue(problems.getFirst().contains("USE TEMP B-TREE FOR ORDER BY"), problems.getFirst());
        }
    }

    @Test
    void aStatementServedByAnIndexInItsOrderIsNotReported() throws Exception {
        try (Connection connection = scratch()) {
            assertEquals(List.of(), problems(connection,
                    Guarded.served("items.by-owner-label", "SELECT * FROM items WHERE owner = ? ORDER BY label, id")));
        }
    }

    @Test
    void anExcusedSortCarriesItsReasonAndPasses() throws Exception {
        try (Connection connection = scratch()) {
            assertEquals(List.of(), problems(connection, new Guarded("items.by-owner-ordered",
                    "SELECT * FROM items WHERE owner = ? ORDER BY label COLLATE NOCASE",
                    "the rows of one owner are bounded by the per-owner budget")));
        }
    }

    @Test
    void anExcuseWithoutAReasonIsRefused() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> new Guarded("items.by-owner-ordered", "SELECT 1", " "));

        assertTrue(refusal.getMessage().contains("without a reason"), refusal.getMessage());
    }

    @Test
    void anExcuseForAStatementThatNoLongerSortsIsReportedAsStale() throws Exception {
        try (Connection connection = scratch()) {
            List<String> problems = problems(connection, new Guarded("items.by-owner-label",
                    "SELECT * FROM items WHERE owner = ? ORDER BY label, id", "sorts rows it holds in memory"));

            assertEquals(1, problems.size(), problems.toString());
            assertTrue(problems.getFirst().contains("no longer scans or sorts"), problems.getFirst());
        }
    }

    private static Connection scratch() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE items (id TEXT PRIMARY KEY, owner TEXT NOT NULL, label TEXT NOT NULL)");
            statement.execute("CREATE INDEX idx_items_owner_label ON items(owner, label, id)");
            statement.execute("CREATE TABLE unrelated (owner TEXT)");
        }
        return connection;
    }

    private static List<String> problems(Connection connection, Guarded guarded) throws SQLException {
        Set<String> tables = tables(connection);
        List<String> flagged = new ArrayList<>();
        boolean searchTermSeen = guarded.searchTerm() == null;
        try (Statement statement = connection.createStatement();
             ResultSet plan = statement.executeQuery("EXPLAIN QUERY PLAN " + guarded.sql())) {
            while (plan.next()) {
                String detail = plan.getString("detail");
                if (scansATable(detail, tables) || detail.contains("USE TEMP B-TREE")) {
                    flagged.add(detail);
                }
                searchTermSeen |= guarded.searchTerm() != null
                        && detail.startsWith("SEARCH ") && detail.contains(guarded.searchTerm());
            }
        }
        if (!searchTermSeen) {
            return List.of(guarded.name() + ": no line of the plan searches on " + guarded.searchTerm());
        }
        if (guarded.excuse() == null) {
            return flagged.stream().map(detail -> guarded.name() + ": " + detail).toList();
        }
        return flagged.isEmpty()
                ? List.of(guarded.name() + ": the plan no longer scans or sorts, remove its excuse: " + guarded.excuse())
                : List.of();
    }

    private static boolean scansATable(String detail, Set<String> tables) {
        if (!detail.startsWith("SCAN ")) {
            return false;
        }
        String target = detail.substring("SCAN ".length()).split("[ (]")[0];
        return tables.contains(target);
    }

    private static Set<String> tables(Connection connection) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rows.next()) {
                tables.add(rows.getString(1));
            }
        }
        return tables;
    }
}
