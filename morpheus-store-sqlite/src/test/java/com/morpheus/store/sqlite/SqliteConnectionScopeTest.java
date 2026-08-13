package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
    void replacesPhysicalConnectionAfterCommittedCleanupFailureWithoutInvalidatingLogicalProxies() throws Exception {
        Path database = temp.resolve("recovery.db");
        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();
        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database);
             Connection first = SqliteDatabaseSecurity.open(database);
             Connection second = SqliteDatabaseSecurity.open(database)) {
            try (var statement = first.createStatement()) {
                statement.execute("CREATE TABLE durable_probe(value INTEGER)");
                statement.execute("INSERT INTO durable_probe(value) VALUES (9)");
            }

            assertTrue(SqliteConnectionScope.recoverAfterCommittedCleanupFailure(
                    first, new java.sql.SQLException("simulated post-commit cleanup failure")));
            assertFalse(first.isClosed());
            assertFalse(second.isClosed());

            try (var statement = second.createStatement();
                 var result = statement.executeQuery("SELECT value FROM durable_probe")) {
                assertTrue(result.next());
                assertEquals(9, result.getInt(1));
            }
            assertEquals(2, scope.logicalConnectionsBorrowed());
        }
        SqliteConnectionScope.Diagnostics after = SqliteConnectionScope.diagnostics();
        assertEquals(before.active(), after.active());
        assertTrue(after.opened() >= before.opened() + 2);
        assertTrue(after.closed() >= before.closed() + 2);
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
    void wrongThreadCloseDoesNotPoisonOwnerCleanup() throws Exception {
        Path database = temp.resolve("owner-thread.db");
        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database)) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread wrongThread = new Thread(() -> {
                try {
                    scope.close();
                } catch (Throwable thrown) {
                    failure.set(thrown);
                }
            }, "wrong-sqlite-scope-owner");
            wrongThread.start();
            wrongThread.join();

            assertInstanceOf(IllegalStateException.class, failure.get());
            try (Connection stillUsable = SqliteDatabaseSecurity.open(database);
                 var statement = stillUsable.createStatement()) {
                statement.execute("SELECT 1");
            }

            scope.close();
        }
        assertEquals(0, SqliteConnectionScope.diagnostics().active());
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
