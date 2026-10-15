package com.morpheus.store.sqlite;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each test opens its scopes with a recorder of its own and reads absolute values there: nothing else in the JVM
 * writes to it. The counters used to be static fields of the process, read as differences.
 */
class SqliteConnectionScopeTest {
    @TempDir
    Path temp;

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);

    @Test
    void aScopeCountsItsConnectionAndItsSchemaCheckOnTheRecorderItsOpenerNamed() {
        Path database = temp.resolve("counted.db");
        OperationalMetrics elsewhere = new OperationalMetrics();

        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database, recorder);
             SqliteSpecificationKnowledgeStore snapshots = new SqliteSpecificationKnowledgeStore(database);
             SqliteCompositionStateStore compositions = new SqliteCompositionStateStore(database)) {
            assertEquals(2, scope.logicalConnectionsBorrowed());
            assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_OPENED));
            assertEquals(0L, counter(SqliteConnectionScope.CONNECTIONS_CLOSED), "the connection is still held");
        }
        try (SqliteConnectionScope other = SqliteConnectionScope.open(
                database, new OperationalRecorder(OperationalEventSink.noop(), elsewhere))) {
            new SqliteSpecificationKnowledgeStore(database).close();
        }

        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_OPENED));
        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_CLOSED));
        assertEquals(1L, counter(SqliteConnectionScope.SCHEMA_CHECKS), "two stores, one schema check");
        assertEquals(Map.of(
                        SqliteConnectionScope.CONNECTIONS_OPENED, 1L,
                        SqliteConnectionScope.CONNECTIONS_CLOSED, 1L,
                        SqliteConnectionScope.SCHEMA_CHECKS, 1L),
                scopeCounters(elsewhere), "a scope opened with another recorder counts there, and only there");
    }

    @Test
    void logicalConnectionsShareOnePhysicalSessionAndCloseIndependently() throws Exception {
        Path database = temp.resolve("morpheus.db");
        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database, recorder)) {
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
        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database, recorder);
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
        assertEquals(2L, counter(SqliteConnectionScope.CONNECTIONS_OPENED), "the first connection and its replacement");
        assertEquals(2L, counter(SqliteConnectionScope.CONNECTIONS_CLOSED), "and both are released");
    }

    @Test
    void rejectsNestedOrDifferentDatabaseScopeBorrow() throws Exception {
        Path firstDatabase = temp.resolve("one.db");
        Path secondDatabase = temp.resolve("two.db");
        try (SqliteConnectionScope ignored = SqliteConnectionScope.open(firstDatabase, recorder)) {
            assertThrows(IllegalStateException.class, () -> SqliteConnectionScope.open(firstDatabase, recorder));
            assertThrows(java.sql.SQLException.class, () -> SqliteDatabaseSecurity.open(secondDatabase));
        }
    }

    @Test
    void wrongThreadCloseDoesNotPoisonOwnerCleanup() throws Exception {
        Path database = temp.resolve("owner-thread.db");
        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database, recorder)) {
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
        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_OPENED));
        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_CLOSED), "the owner released what it opened");
    }

    @Test
    void schemaRunsOnceAndRollbackRemainsVisibleAcrossLogicalConnections() throws Exception {
        Path database = temp.resolve("transaction.db");
        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database, recorder);
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
        assertEquals(1L, counter(SqliteConnectionScope.SCHEMA_CHECKS));
        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_OPENED));
        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_CLOSED));
    }

    private long counter(String name) {
        return metrics.snapshot().counters().getOrDefault(name, 0L);
    }

    private static Map<String, Long> scopeCounters(OperationalMetrics observed) {
        Map<String, Long> scope = new TreeMap<>(observed.snapshot().counters());
        scope.keySet().removeIf(name -> !name.startsWith("sqlite.scope."));
        return scope;
    }
}
