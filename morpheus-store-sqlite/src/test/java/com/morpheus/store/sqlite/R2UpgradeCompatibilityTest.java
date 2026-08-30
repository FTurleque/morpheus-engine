package com.morpheus.store.sqlite;

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
import java.sql.Types;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R2UpgradeCompatibilityTest {

    private static final List<BaselineMigration> ONE_DOT_ZERO_MIGRATIONS = List.of(
            new BaselineMigration(1, "foundation", "/db/migration/V001__foundation.sql"),
            new BaselineMigration(2, "project-root-uniqueness", "/db/migration/V002__project_root_uniqueness.sql"),
            new BaselineMigration(3, "entity-identity-bindings", "/db/migration/V003__entity_identity_bindings.sql"),
            new BaselineMigration(4, "versioned-requirement-persistence", "/db/migration/V004__versioned_requirement_persistence.sql"),
            new BaselineMigration(5, "snapshot-traceability-persistence", "/db/migration/V005__snapshot_traceability_persistence.sql"),
            new BaselineMigration(6, "snapshot-external-reference-persistence", "/db/migration/V006__snapshot_external_reference_persistence.sql"),
            new BaselineMigration(7, "snapshot-business-content-projection", "/db/migration/V007__snapshot_business_content_projection.sql"),
            new BaselineMigration(8, "sync-state-and-source-inventory", "/db/migration/V008__sync_state_and_source_inventory.sql"),
            new BaselineMigration(9, "snapshot-acceptance-criteria", "/db/migration/V009__snapshot_acceptance_criteria.sql"),
            new BaselineMigration(10, "constraint-semantics", "/db/migration/V010__constraint_semantics.sql"),
            new BaselineMigration(11, "controlled-lifecycle-mutations", "/db/migration/V011__controlled_lifecycle_mutations.sql"),
            new BaselineMigration(12, "multi-provider-composition", "/db/migration/V012__multi_provider_composition.sql"));

    @TempDir
    Path tempDir;

    @Test
    void oneDotZeroSchemaMigratesToV17WithoutIdentityOrHistoryLoss() throws Exception {
        Path database = tempDir.resolve("morpheus-1.0.0.db");
        Map<Integer, String> baselineChecksums;

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            baselineChecksums = applyOneDotZeroBaseline(connection);
            insertPublishedOneDotZeroState(connection);

            assertEquals(12, new SqliteSchemaManager().currentVersion(connection));
            assertEquals("file", scalar(connection, "SELECT root_scheme FROM projects WHERE id = 'project-r2'"));
            assertEquals("ACTIVE", scalar(connection, "SELECT state FROM knowledge_snapshots WHERE id = 'snapshot-r2'"));
            assertEquals("release-1.0.0", scalar(connection,
                    "SELECT source_revision FROM knowledge_snapshots WHERE id = 'snapshot-r2'"));
        }

        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Opening with the current runtime applies V013 through V017.
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertEquals(17, new SqliteSchemaManager().currentVersion(connection));
            assertEquals(17, integerScalar(connection, "SELECT COUNT(*) FROM schema_migrations"));

            assertEquals("file", scalar(connection, "SELECT root_scheme FROM projects WHERE id = 'project-r2'"));
            assertEquals("workspace-r2", scalar(connection, "SELECT root_value FROM projects WHERE id = 'project-r2'"));
            assertEquals("project-r2", scalar(connection,
                    "SELECT project_id FROM knowledge_snapshots WHERE id = 'snapshot-r2'"));
            assertEquals("ACTIVE", scalar(connection, "SELECT state FROM knowledge_snapshots WHERE id = 'snapshot-r2'"));
            assertEquals("release-1.0.0", scalar(connection,
                    "SELECT source_revision FROM knowledge_snapshots WHERE id = 'snapshot-r2'"));

            assertTrue(tableExists(connection, "portfolios"));
            assertTrue(tableExists(connection, "saved_views"));
            assertTrue(tableExists(connection, "policy_packs"));
            assertTrue(tableExists(connection, "specification_version_sequences"));
            assertTrue(indexExists(connection, "uq_specification_versions_project_sequence"));

            for (Map.Entry<Integer, String> baseline : baselineChecksums.entrySet()) {
                assertEquals(baseline.getValue(), scalar(connection,
                        "SELECT checksum FROM schema_migrations WHERE version = " + baseline.getKey()),
                        "Historical migration checksum changed for V" + baseline.getKey());
            }
        }

        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // A second current-runtime startup must not replay an already applied migration.
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertEquals(17, integerScalar(connection, "SELECT COUNT(*) FROM schema_migrations"));
            assertEquals(1, integerScalar(connection, "SELECT COUNT(*) FROM projects WHERE id = 'project-r2'"));
            assertEquals(1, integerScalar(connection,
                    "SELECT COUNT(*) FROM knowledge_snapshots WHERE id = 'snapshot-r2'"));
        }
    }

    private Map<Integer, String> applyOneDotZeroBaseline(Connection connection) throws Exception {
        connection.setAutoCommit(false);
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

        Map<Integer, String> checksums = new LinkedHashMap<>();
        for (BaselineMigration migration : ONE_DOT_ZERO_MIGRATIONS) {
            String script = loadScript(migration.resourcePath());
            String checksum = sha256(script);
            executeScript(connection, script);
            try (var statement = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, name, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.name());
                statement.setString(3, checksum);
                statement.setString(4, Instant.parse("2026-07-27T00:00:00Z").toString());
                statement.executeUpdate();
            }
            checksums.put(migration.version(), checksum);
        }
        connection.commit();
        connection.setAutoCommit(true);
        return checksums;
    }

    private void insertPublishedOneDotZeroState(Connection connection) throws Exception {
        try (var project = connection.prepareStatement(
                "INSERT INTO projects(id, root_scheme, root_value) VALUES (?, ?, ?)");
             var snapshot = connection.prepareStatement("""
                     INSERT INTO knowledge_snapshots(
                         id, project_id, predecessor_id, state, source_revision, created_at)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            project.setString(1, "project-r2");
            project.setString(2, "file");
            project.setString(3, "workspace-r2");
            project.executeUpdate();

            snapshot.setString(1, "snapshot-r2");
            snapshot.setString(2, "project-r2");
            snapshot.setNull(3, Types.VARCHAR);
            snapshot.setString(4, "ACTIVE");
            snapshot.setString(5, "release-1.0.0");
            snapshot.setString(6, "2026-07-27T00:00:00Z");
            snapshot.executeUpdate();
        }
    }

    private String loadScript(String resourcePath) throws IOException {
        try (var stream = R2UpgradeCompatibilityTest.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Missing migration resource " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void executeScript(Connection connection, String script) throws Exception {
        for (String fragment : script.split(";")) {
            String sql = fragment.trim();
            if (sql.isEmpty()) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean indexExists(Connection connection, String index) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?")) {
            statement.setString(1, index);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private String scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), "No result for: " + sql);
            return result.getString(1);
        }
    }

    private int integerScalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), "No result for: " + sql);
            return result.getInt(1);
        }
    }

    private record BaselineMigration(int version, String name, String resourcePath) {
    }
}
