package com.morpheus.store.sqlite.runtime;

import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A database at the schema of the published 1.2.0 (version 15) that holds rows in every table which carried a repeated
 * index is opened by the current store: the repeated indexes go, the rows stay, and what the dropped indexes did not
 * duplicate stays too.
 */
class SqliteIndexRemovalUpgradeTest {

    private static final int PUBLISHED_SCHEMA_VERSION = 15;

    private static final List<String> MIGRATION_RESOURCES = List.of(
            "V001__foundation.sql",
            "V002__project_root_uniqueness.sql",
            "V003__entity_identity_bindings.sql",
            "V004__versioned_requirement_persistence.sql",
            "V005__snapshot_traceability_persistence.sql",
            "V006__snapshot_external_reference_persistence.sql",
            "V007__snapshot_business_content_projection.sql",
            "V008__sync_state_and_source_inventory.sql",
            "V009__snapshot_acceptance_criteria.sql",
            "V010__constraint_semantics.sql",
            "V011__controlled_lifecycle_mutations.sql",
            "V012__multi_provider_composition.sql",
            "V013__portfolio_intelligence.sql",
            "V014__saved_views.sql",
            "V015__policy_packs.sql");

    private static final List<String> TABLES_THAT_CARRIED_A_REPEATED_INDEX = List.of(
            "saved_view_versions",
            "snapshot_traceability_links",
            "policy_pack_activations",
            "policy_overrides",
            "policy_pack_versions",
            "sync_inventory_entries",
            "snapshot_constraint_blocking_targets",
            "snapshot_constraint_supporting_evidence",
            "snapshot_specifications",
            "snapshot_scenarios",
            "snapshot_changes");

    private static final List<String> INDEXES_THAT_CARRY_OTHER_COLUMNS_OR_ANOTHER_ORDER = List.of(
            "idx_snapshot_constraints_change",
            "idx_snapshot_acceptance_requirement",
            "idx_portfolio_memberships_project",
            "idx_requirement_versions_snapshot");

    @TempDir
    Path tempDir;

    @Test
    void aDatabaseAtThePublishedSchemaLosesTheRepeatedIndexesAndNothingElse() throws Exception {
        Path database = tempDir.resolve("published.db");
        Map<String, Integer> rowsBefore = new TreeMap<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            applyPublishedSchema(connection);
            assertEquals(PUBLISHED_SCHEMA_VERSION, new SqliteSchemaManager().currentVersion(connection));
            assertTrue(SqliteIndexRepetitions.find(connection).size() >= TABLES_THAT_CARRIED_A_REPEATED_INDEX.size(),
                    "the published schema must carry the repeated indexes this test is about");
            for (String table : TABLES_THAT_CARRIED_A_REPEATED_INDEX) {
                insertOneRow(connection, table);
                rowsBefore.put(table, count(connection, table));
            }
        }

        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Opening with the current runtime applies every migration after the published schema.
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertEquals(List.of(), SqliteIndexRepetitions.find(connection));
            Map<String, Integer> rowsAfter = new TreeMap<>();
            for (String table : TABLES_THAT_CARRIED_A_REPEATED_INDEX) {
                rowsAfter.put(table, count(connection, table));
            }
            assertEquals(rowsBefore, rowsAfter);
            for (String index : INDEXES_THAT_CARRY_OTHER_COLUMNS_OR_ANOTHER_ORDER) {
                assertTrue(indexExists(connection, index), "index " + index + " must survive the migration");
            }
            assertTrue(indexExists(connection, "idx_requirement_versions_current_order"));
            assertTrue(indexExists(connection, "idx_saved_views_scope"));
        }
    }

    @Test
    void uniquenessAndForeignKeysAreStillEnforcedOnATableThatLostAnIndex() throws Exception {
        Path database = tempDir.resolve("migrated.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // The constructor applies every migration.
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("INSERT INTO policy_packs(id, name, revision, latest_version_number, created_at, updated_at) "
                    + "VALUES ('pack', 'pack', 1, 1, 'now', 'now')");
            statement.execute("INSERT INTO policy_pack_versions(pack_id, version_id, version_number, encoded_version, created_at) "
                    + "VALUES ('pack', 'v1', 1, 'encoded', 'now')");

            SQLException duplicateVersionNumber = assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO policy_pack_versions(pack_id, version_id, version_number, encoded_version, created_at) "
                            + "VALUES ('pack', 'v1-again', 1, 'encoded', 'now')"));
            assertTrue(duplicateVersionNumber.getMessage().contains("policy_pack_versions.version_number"),
                    duplicateVersionNumber.getMessage());

            SQLException duplicatePrimaryKey = assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO policy_pack_versions(pack_id, version_id, version_number, encoded_version, created_at) "
                            + "VALUES ('other', 'v1', 1, 'encoded', 'now')"));
            assertTrue(duplicatePrimaryKey.getMessage().contains("policy_pack_versions.version_id"),
                    duplicatePrimaryKey.getMessage());

            SQLException orphan = assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO saved_view_versions(saved_view_id, revision, name, query_definition, status, recorded_at) "
                            + "VALUES ('absent', 1, 'name', 'query', 'ACTIVE', 'now')"));
            assertTrue(orphan.getMessage().contains("FOREIGN KEY"), orphan.getMessage());
        }
    }

    private static void applyPublishedSchema(Connection connection) throws Exception {
        SqliteSchemaManager manager = new SqliteSchemaManager();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE schema_migrations (
                        version INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        checksum TEXT NOT NULL,
                        applied_at TEXT NOT NULL
                    )
                    """);
        }
        for (int index = 0; index < MIGRATION_RESOURCES.size(); index++) {
            int version = index + 1;
            String resource = MIGRATION_RESOURCES.get(index);
            manager.executeScript(connection, script(resource));
            try (var statement = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, name, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
                statement.setInt(1, version);
                statement.setString(2, migrationName(resource));
                statement.setString(3, SqliteMigrationChecksumGoldenTest.CANONICAL_CHECKSUMS.get(version));
                statement.setString(4, Instant.parse("2026-07-27T00:00:00Z").toString());
                statement.executeUpdate();
            }
        }
    }

    /** The name a migration records: its file name without version or extension, underscores read as hyphens. */
    private static String migrationName(String resource) {
        return resource.substring(resource.indexOf("__") + 2, resource.length() - ".sql".length()).replace('_', '-');
    }

    private static String script(String resource) throws IOException {
        try (var stream = SqliteIndexRemovalUpgradeTest.class.getResourceAsStream("/db/migration/" + resource)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    /** One row whose every column holds a value its type and the table's checks accept; foreign keys are not enforced. */
    private static void insertOneRow(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<String> values = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT * FROM pragma_table_info(?)")) {
            statement.setString(1, table);
            try (ResultSet columnsOfTable = statement.executeQuery()) {
                while (columnsOfTable.next()) {
                    String column = columnsOfTable.getString("name");
                    columns.add(column);
                    values.add(valueFor(column, columnsOfTable.getString("type")));
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO " + table + "(" + String.join(", ", columns) + ") VALUES ("
                    + String.join(", ", values) + ")");
        }
    }

    private static String valueFor(String column, String type) {
        if (column.equals("scope_kind")) {
            return "'PROJECT'";
        }
        if (column.equals("status")) {
            return "'ACTIVE'";
        }
        return type.toUpperCase().contains("INT") ? "1" : "'" + column + "'";
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static boolean indexExists(Connection connection, String name) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?")) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
}
