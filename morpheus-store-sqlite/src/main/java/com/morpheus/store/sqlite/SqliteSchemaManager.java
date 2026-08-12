package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** Minimal explicit SQL migration runner for the embedded SQLite adapter. */
final class SqliteSchemaManager {
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "foundation", "/db/migration/V001__foundation.sql"),
            new Migration(2, "project-root-uniqueness", "/db/migration/V002__project_root_uniqueness.sql"),
            new Migration(3, "entity-identity-bindings", "/db/migration/V003__entity_identity_bindings.sql"),
            new Migration(4, "versioned-requirement-persistence", "/db/migration/V004__versioned_requirement_persistence.sql"),
            new Migration(5, "snapshot-traceability-persistence", "/db/migration/V005__snapshot_traceability_persistence.sql"),
            new Migration(6, "snapshot-external-reference-persistence", "/db/migration/V006__snapshot_external_reference_persistence.sql"),
            new Migration(7, "snapshot-business-content-projection", "/db/migration/V007__snapshot_business_content_projection.sql"),
            new Migration(8, "sync-state-and-source-inventory", "/db/migration/V008__sync_state_and_source_inventory.sql"),
            new Migration(9, "snapshot-acceptance-criteria", "/db/migration/V009__snapshot_acceptance_criteria.sql"),
            new Migration(10, "constraint-semantics", "/db/migration/V010__constraint_semantics.sql"),
            new Migration(11, "controlled-lifecycle-mutations", "/db/migration/V011__controlled_lifecycle_mutations.sql"),
            new Migration(12, "multi-provider-composition", "/db/migration/V012__multi_provider_composition.sql"),
            new Migration(13, "portfolio-intelligence", "/db/migration/V013__portfolio_intelligence.sql"),
            new Migration(14, "saved-views", "/db/migration/V014__saved_views.sql"),
            new Migration(15, "policy-packs", "/db/migration/V015__policy_packs.sql"));
    static final int SUPPORTED_SCHEMA_VERSION = 15;

    void migrate(Connection connection) {
        if (SqliteConnectionScope.schemaReadyIfActive()) return;
        SqliteTransactionRunner.runVoid(connection, "SQLite schema migration failed", current -> {
            createMigrationLedger(current);
            rejectFutureSchema(current);

            for (Migration migration : MIGRATIONS) {
                applyMigration(current, migration);
            }
        });
        SqliteConnectionScope.markSchemaReadyIfActive();
    }

    int currentVersion(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read SQLite schema version", exception);
        }
    }

    private void createMigrationLedger(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        checksum TEXT NOT NULL,
                        applied_at TEXT NOT NULL
                    )
                    """);
        }
    }

    private void rejectFutureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
            int version = result.next() ? result.getInt(1) : 0;
            if (version > SUPPORTED_SCHEMA_VERSION) {
                throw new KnowledgeStoreException(
                        "SQLite schema version " + version + " is newer than supported " + SUPPORTED_SCHEMA_VERSION);
            }
        }
    }

    private void applyMigration(Connection connection, Migration migration) throws SQLException {
        String script = loadScript(migration.resourcePath());
        String checksum = sha256(script);
        AppliedMigration applied = findAppliedMigration(connection, migration.version());

        if (applied != null) {
            if (!applied.name().equals(migration.name()) || !applied.checksum().equals(checksum)) {
                throw new KnowledgeStoreException(
                        "SQLite migration history mismatch for version " + migration.version());
            }
            return;
        }

        executeScript(connection, script);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema_migrations(version, name, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.name());
            statement.setString(3, checksum);
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private AppliedMigration findAppliedMigration(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name, checksum FROM schema_migrations WHERE version = ?")) {
            statement.setInt(1, version);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new AppliedMigration(result.getString("name"), result.getString("checksum"));
            }
        }
    }

    void executeScript(Connection connection, String script) throws SQLException {
        for (String sql : SqliteSqlStatements.split(script)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    private String loadScript(String resourcePath) {
        try (var stream = SqliteSchemaManager.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new KnowledgeStoreException("SQLite migration resource not found: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new KnowledgeStoreException("Cannot read SQLite migration resource: " + resourcePath, exception);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private record Migration(int version, String name, String resourcePath) {
    }

    private record AppliedMigration(String name, String checksum) {
    }
}
