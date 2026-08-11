package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteConnectionScopeTest {
    @TempDir
    Path temp;

    @Test
    void logicalConnectionsShareOnePhysicalSessionAndCloseIndependently() throws Exception {
        Path database = temp.resolve("morpheus.db");
        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database)) {
            Connection first = SqliteDatabaseSecurity.open(database);
            Connection second = SqliteDatabaseSecurity.open(database);
            try (var statement = first.createStatement()) {
                statement.execute("CREATE TEMP TABLE scoped_probe(value INTEGER)");
                statement.execute("INSERT INTO scoped_probe(value) VALUES (7)");
            }
            try (var statement = second.createStatement();
                 var result = statement.executeQuery("SELECT value FROM scoped_probe")) {
                result.next();
                assertEquals(7, result.getInt(1));
            }
            assertEquals(2, scope.logicalConnectionsBorrowed());
            first.close();
            assertThrows(java.sql.SQLException.class, first::createStatement);
            try (var statement = second.createStatement()) {
                statement.execute("SELECT 1");
            }
            second.close();
        }
    }

    @Test
    void rejectsNestedOrDifferentDatabaseScopeBorrow() throws Exception {
        Path firstDatabase = temp.resolve("one.db");
        Path secondDatabase = temp.resolve("two.db");
        try (SqliteConnectionScope ignored = SqliteConnectionScope.open(firstDatabase)) {
            assertThrows(IllegalStateException.class, () -> SqliteConnectionScope.open(firstDatabase));
            assertThrows(java.sql.SQLException.class, () -> SqliteDatabaseSecurity.open(secondDatabase));
        }
    }

    @Test
    void schemaRunsOnceAndRollbackRemainsVisibleAcrossLogicalConnections() throws Exception {
        Path database = temp.resolve("transaction.db");
        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database);
             Connection first = SqliteDatabaseSecurity.open(database);
             Connection second = SqliteDatabaseSecurity.open(database)) {
            SqliteSchemaManager migrations = new SqliteSchemaManager();
            migrations.migrate(first);
            migrations.migrate(second);
            assertEquals(1, scope.schemaInitializations());

            try (var statement = first.createStatement()) {
                statement.execute("CREATE TABLE rollback_probe(value INTEGER)");
            }
            first.setAutoCommit(false);
            try (var statement = first.createStatement()) {
                statement.execute("INSERT INTO rollback_probe(value) VALUES (1)");
            }
            first.rollback();
            first.setAutoCommit(true);
            try (var statement = second.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM rollback_probe")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
        SqliteConnectionScope.Diagnostics diagnostics = SqliteConnectionScope.diagnostics();
        assertEquals(0, diagnostics.active());
        assertTrue(diagnostics.opened() >= diagnostics.closed());
    }
}
