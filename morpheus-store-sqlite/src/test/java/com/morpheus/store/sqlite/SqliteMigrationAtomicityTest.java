package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every pending migration is applied in one transaction, so a migration that fails midway leaves the database exactly
 * as it was: the ledger at its previous version, no object of a later migration, the data untouched, and the next
 * opening free to succeed once the cause is removed (STO-AUD-4).
 */
class SqliteMigrationAtomicityTest {
    private static final List<String> THROUGH_V012 = List.of(
            "V001__foundation", "V002__project_root_uniqueness", "V003__entity_identity_bindings",
            "V004__versioned_requirement_persistence", "V005__snapshot_traceability_persistence",
            "V006__snapshot_external_reference_persistence", "V007__snapshot_business_content_projection",
            "V008__sync_state_and_source_inventory", "V009__snapshot_acceptance_criteria", "V010__constraint_semantics",
            "V011__controlled_lifecycle_mutations", "V012__multi_provider_composition");

    @TempDir
    Path tempDir;

    @Test
    void aMigrationFailingMidwayLeavesTheDatabaseAtItsPreviousVersion() throws Exception {
        Path database = tempDir.resolve("version-12.db");
        Set<String> objectsBefore;
        try (Connection connection = connect(database)) {
            applyThroughVersion12(connection);
            execute(connection, "INSERT INTO projects(id, root_scheme, root_value) VALUES ('project-12', 'file', 'ws')");
            // V014 creates this table: V013 applies in full, then V014 fails on a leftover of the same name, so
            // only a rollback spanning every pending migration can leave the database at version 12.
            execute(connection, "CREATE TABLE saved_views (leftover TEXT)");
            objectsBefore = schemaObjects(connection);
        }

        KnowledgeStoreException refused = assertThrows(KnowledgeStoreException.class, () -> {
            try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
                // Opening migrates, and must fail before the store is usable.
            }
        });

        assertEquals("SQLite schema migration failed", refused.getMessage());
        assertTrue(String.valueOf(refused.getCause()).contains("saved_views"), () -> String.valueOf(refused.getCause()));
        try (Connection connection = connect(database)) {
            assertEquals(12, ledgerVersion(connection));
            assertEquals(objectsBefore, schemaObjects(connection), "no object of V013 to V020 may survive the failure");
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM projects WHERE id = 'project-12'"));
            execute(connection, "DROP TABLE saved_views");
        }

        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // With the conflict removed, the same opening applies V013 to V020.
        }
        try (Connection connection = connect(database)) {
            assertEquals(SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION, ledgerVersion(connection));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM projects WHERE id = 'project-12'"));
        }
    }

    private static void applyThroughVersion12(Connection connection) throws Exception {
        execute(connection, """
                CREATE TABLE schema_migrations (
                    version INTEGER PRIMARY KEY,
                    name TEXT NOT NULL,
                    checksum TEXT NOT NULL,
                    applied_at TEXT NOT NULL
                )
                """);
        for (int index = 0; index < THROUGH_V012.size(); index++) {
            String resource = THROUGH_V012.get(index);
            String script = load("/db/migration/" + resource + ".sql");
            for (String fragment : script.split(";")) {
                if (!fragment.isBlank()) execute(connection, fragment);
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, name, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
                insert.setInt(1, index + 1);
                insert.setString(2, resource.substring(resource.indexOf("__") + 2).replace('_', '-'));
                insert.setString(3, sha256(script));
                insert.setString(4, "2026-10-08T00:00:00Z");
                insert.executeUpdate();
            }
        }
    }

    private static Set<String> schemaObjects(Connection connection) throws Exception {
        Set<String> objects = new TreeSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT type || ':' || name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'")) {
            while (result.next()) objects.add(result.getString(1));
        }
        return objects;
    }

    private static int ledgerVersion(Connection connection) throws Exception {
        return count(connection, "SELECT COALESCE(MAX(version), 0) FROM schema_migrations");
    }

    private static int count(Connection connection, String query) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(query)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Connection connect(Path database) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
    }

    private static String load(String resource) throws IOException {
        try (var stream = SqliteMigrationAtomicityTest.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("missing migration resource " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
