package com.morpheus.store.sqlite;

import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQLite write contention must be observable before it becomes an outage.
 *
 * <p>SQLite serializes writers, so a contended database is a slower one right up to the moment the busy timeout
 * is exhausted and an operation fails outright. Nothing in between produced any signal, which is precisely the
 * range in which an operator could still act. Counters are read as deltas here, because the operational runtime
 * is process-local and shared with everything else running in this JVM.</p>
 */
class SqliteContentionObservabilityTest {
    private static final int CONTENDED_BUSY_TIMEOUT_MILLIS = 150;

    @TempDir
    Path tempDir;

    @Test
    void aCommittedTransactionIsCountedAndTimed() {
        Path database = tempDir.resolve("committed.db");
        Map<String, Long> before = counters();

        try (SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(database)) {
            store.putProject(new ProjectStoreEntry(
                    ProjectSpecificationId.generate(), SourceLocator.file("contention/committed")));
        }

        Map<String, Long> after = counters();
        assertTrue(delta(before, after, SqliteContentionMetrics.TRANSACTIONS_STARTED) > 0L,
                "opening and migrating a store must be counted as a started transaction");
        assertTrue(delta(before, after, SqliteContentionMetrics.TRANSACTIONS_COMMITTED) > 0L,
                "a durable transaction must be counted as committed");
        assertEquals(0L, delta(before, after, SqliteContentionMetrics.CONTENDED_TRANSACTIONS),
                "an uncontended write must not be reported as contention");
        assertTrue(LocalOperationalRuntime.metrics().snapshot().timings()
                        .containsKey(SqliteContentionMetrics.TRANSACTION_DURATION),
                "transaction duration must be recorded so a slowing database is visible before it fails");
    }

    /**
     * A transaction blocked by an exclusive writer past its busy timeout is the failure an operator must be
     * able to see coming, so it is attributed to contention rather than to generic transaction failure.
     */
    @Test
    void aTransactionLosingToAnExclusiveWriterIsAttributedToContention() throws Exception {
        Path database = bootstrappedDatabase("contended.db");

        try (Connection locker = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath().normalize());
             Connection contender = SqliteDatabaseSecurity.openPhysical(
                     database, CONTENDED_BUSY_TIMEOUT_MILLIS)) {
            try (Statement statement = locker.createStatement()) {
                statement.execute("PRAGMA busy_timeout = " + CONTENDED_BUSY_TIMEOUT_MILLIS);
                statement.execute("BEGIN EXCLUSIVE");
            }

            Map<String, Long> before = counters();
            assertThrows(KnowledgeStoreException.class, () -> insertProbeRow(contender));
            Map<String, Long> after = counters();

            assertEquals(1L, delta(before, after, SqliteContentionMetrics.CONTENDED_TRANSACTIONS),
                    "a transaction that lost to an exclusive writer must be attributed to SQLite contention");
            assertEquals(1L, delta(before, after, SqliteContentionMetrics.TRANSACTIONS_ROLLED_BACK),
                    "a contended transaction must also be counted as rolled back");
            assertEquals(0L, delta(before, after, SqliteContentionMetrics.TRANSACTIONS_COMMITTED),
                    "a refused transaction must never be counted as committed");

            try (Statement statement = locker.createStatement()) {
                statement.execute("ROLLBACK");
            }

            Map<String, Long> beforeRecovery = counters();
            insertProbeRow(contender);
            Map<String, Long> afterRecovery = counters();
            assertEquals(0L, delta(beforeRecovery, afterRecovery, SqliteContentionMetrics.CONTENDED_TRANSACTIONS),
                    "the transaction that succeeds once the lock is released is not contention");
            assertEquals(1L, delta(beforeRecovery, afterRecovery, SqliteContentionMetrics.TRANSACTIONS_COMMITTED));
        }
    }

    /** A business failure inside a transaction is a rollback, never SQLite contention. */
    @Test
    void anOrdinaryTransactionFailureIsNotReportedAsContention() throws Exception {
        Path database = bootstrappedDatabase("business-failure.db");

        Map<String, Long> before = counters();
        try (Connection connection = SqliteDatabaseSecurity.openPhysical(
                database, SqliteDatabaseSecurity.DEFAULT_BUSY_TIMEOUT_MILLIS)) {
            assertThrows(IllegalStateException.class, () -> SqliteTransactionRunner.runVoid(
                    connection,
                    "deliberate failure",
                    current -> {
                        throw new IllegalStateException("business rule violated");
                    }));
        }
        Map<String, Long> after = counters();

        assertEquals(1L, delta(before, after, SqliteContentionMetrics.TRANSACTIONS_ROLLED_BACK));
        assertEquals(0L, delta(before, after, SqliteContentionMetrics.CONTENDED_TRANSACTIONS));
    }

    private Path bootstrappedDatabase(String name) {
        Path database = tempDir.resolve(name);
        try (SqliteSpecificationKnowledgeStore bootstrap = new SqliteSpecificationKnowledgeStore(database)) {
            // Creates and hardens the schema before the contention scenario opens its own connections.
        }
        return database;
    }

    private void insertProbeRow(Connection connection) {
        SqliteTransactionRunner.runVoid(connection, "Cannot store project", current -> {
            try (PreparedStatement statement = current.prepareStatement(
                    "INSERT INTO projects(id, root_scheme, root_value) VALUES (?, 'file', ?)")) {
                statement.setString(1, ProjectSpecificationId.generate().toString());
                statement.setString(2, "contention/probe-" + System.nanoTime());
                statement.executeUpdate();
            }
        });
    }

    private static Map<String, Long> counters() {
        return LocalOperationalRuntime.metrics().snapshot().counters();
    }

    private static long delta(Map<String, Long> before, Map<String, Long> after, String counter) {
        return after.getOrDefault(counter, 0L) - before.getOrDefault(counter, 0L);
    }
}
