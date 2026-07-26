package com.morpheus.store.sqlite;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteMigrationCompatibilityM19Test {

    @TempDir
    Path tempDir;

    @Test
    void databaseSimulatedJustBeforeV012ReappliesCompositionMigrationWithoutLosingEarlierData() throws Exception {
        Path database = tempDir.resolve("migration-v011-to-v012.db");
        ProjectStoreEntry project = new ProjectStoreEntry(
                ProjectSpecificationId.generate(),
                SourceLocator.file("m19/migration-project"));

        try (SqliteSpecificationKnowledgeStore current = new SqliteSpecificationKnowledgeStore(database)) {
            current.putProject(project);
        }

        List<String> compositionTables;
        String migrationTable;
        long v012RowId;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath().normalize())) {
            compositionTables = compositionTables(connection);
            assertFalse(compositionTables.isEmpty(), "current schema must contain V012 composition tables");
            migrationTable = findMigrationTable(connection);
            v012RowId = findV012MigrationRowId(connection, migrationTable);

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = OFF");
                for (String table : compositionTables) {
                    statement.execute("DROP TABLE " + quoteIdentifier(table));
                }
                statement.execute("DELETE FROM " + quoteIdentifier(migrationTable) + " WHERE rowid = " + v012RowId);
                statement.execute("PRAGMA foreign_keys = ON");
            }
        }

        try (SqliteSpecificationKnowledgeStore migrated = new SqliteSpecificationKnowledgeStore(database)) {
            assertEquals(project, migrated.findProject(project.id()).orElseThrow(),
                    "data written before V012 must survive the migration");
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath().normalize())) {
            List<String> recreated = compositionTables(connection);
            assertEquals(compositionTables, recreated,
                    "reopening the store must recreate the exact V012 composition table set");
            assertTrue(findV012MigrationRowId(connection, findMigrationTable(connection)) > 0L,
                    "V012 migration must be recorded again after successful upgrade");
        }
    }

    private List<String> compositionTables(Connection connection) throws Exception {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                  AND lower(name) LIKE '%composition%'
                ORDER BY name
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }
        return List.copyOf(tables);
    }

    private String findMigrationTable(Connection connection) throws Exception {
        List<String> candidates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                  AND lower(name) LIKE '%migration%'
                ORDER BY name
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                candidates.add(result.getString(1));
            }
        }
        if (candidates.size() != 1) {
            throw new AssertionError("expected exactly one migration tracking table but found " + candidates);
        }
        return candidates.getFirst();
    }

    private long findV012MigrationRowId(Connection connection, String migrationTable) throws Exception {
        String sql = "SELECT rowid, * FROM " + quoteIdentifier(migrationTable) + " ORDER BY rowid";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = result.getMetaData();
            while (result.next()) {
                StringBuilder row = new StringBuilder();
                for (int column = 2; column <= metadata.getColumnCount(); column++) {
                    Object value = result.getObject(column);
                    if (value != null) {
                        row.append(value).append('|');
                    }
                }
                String normalized = row.toString().toLowerCase(Locale.ROOT);
                if (normalized.contains("v012") || normalized.contains("multi_provider_composition")) {
                    return result.getLong(1);
                }
            }
        }
        throw new AssertionError("V012 migration row was not found in " + migrationTable);
    }

    private String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
