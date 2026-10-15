package com.morpheus.store.sqlite;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQLite write contention must be observable before it becomes an outage.
 *
 * <p>SQLite serializes writers, so a contended database is a slower one right up to the moment the busy timeout
 * is exhausted and an operation fails outright. Nothing in between produced any signal, which is precisely the
 * range in which an operator could still act.</p>
 *
 * <p>Each test opens its connection or its scope with metrics of its own and asserts the values it finds there:
 * nothing else in the JVM writes to them.</p>
 */
class SqliteContentionObservabilityTest {
    private static final int CONTENDED_BUSY_TIMEOUT_MILLIS = 150;

    @TempDir
    Path tempDir;

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);

    @Test
    void aCommittedTransactionIsCountedAndTimed() {
        Path database = tempDir.resolve("committed.db");

        try (SqliteConnectionScope observed = SqliteConnectionScope.open(database, recorder);
             SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(database)) {
            store.putProject(new ProjectStoreEntry(
                    ProjectSpecificationId.generate(), SourceLocator.file("contention/committed")));
        }

        long started = counter(SqliteContentionMetrics.TRANSACTIONS_STARTED);
        assertTrue(started > 0L, "opening and migrating a store must be counted as a started transaction");
        assertEquals(started, counter(SqliteContentionMetrics.TRANSACTIONS_COMMITTED),
                "every transaction of an uncontended store commits, and each is counted once");
        assertEquals(Set.of(SqliteContentionMetrics.TRANSACTIONS_STARTED, SqliteContentionMetrics.TRANSACTIONS_COMMITTED,
                        SqliteConnectionScope.CONNECTIONS_OPENED, SqliteConnectionScope.CONNECTIONS_CLOSED,
                        SqliteConnectionScope.SCHEMA_CHECKS),
                metrics.snapshot().counters().keySet(),
                "an uncontended write is reported neither as a rollback nor as contention; its scope is counted");
        assertEquals(started, metrics.snapshot().timings().get(SqliteContentionMetrics.TRANSACTION_DURATION).count(),
                "transaction duration must be recorded so a slowing database is visible before it fails");
    }

    /** The counters belong to whoever opened the connection: a second store, opened by someone else, moves none. */
    @Test
    void twoStoresOpenedWithTwoRecordersMoveTwoSetsOfCounters() {
        OperationalMetrics otherMetrics = new OperationalMetrics();
        Path otherDatabase = tempDir.resolve("other.db");
        try (SqliteConnectionScope other = SqliteConnectionScope.open(
                     otherDatabase, new OperationalRecorder(OperationalEventSink.noop(), otherMetrics));
             SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(otherDatabase)) {
            // Built and migrated: what it cost is on the other metrics, and stays there.
        }
        Map<String, Long> otherAfterItsOwnWork = otherMetrics.snapshot().counters();
        assertTrue(otherAfterItsOwnWork.get(SqliteContentionMetrics.TRANSACTIONS_COMMITTED) > 0L);
        assertEquals(Map.of(), metrics.snapshot().counters(), "a store opened by someone else was counted here");

        Path database = tempDir.resolve("mine.db");
        try (SqliteConnectionScope mine = SqliteConnectionScope.open(database, recorder);
             SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(database)) {
            store.putProject(new ProjectStoreEntry(
                    ProjectSpecificationId.generate(), SourceLocator.file("contention/mine")));
        }

        assertTrue(counter(SqliteContentionMetrics.TRANSACTIONS_COMMITTED) > 0L,
                "the transactions of a store are counted on the recorder its scope was opened with");
        assertEquals(otherAfterItsOwnWork, otherMetrics.snapshot().counters(),
                "a transaction of one store moved the counters of another");
    }

    /** A store opened outside any scope, by an opener that named a recorder, is observed by that recorder too. */
    @Test
    void aConnectionOpenedWithARecorderOutsideAnyScopeCountsThere() throws Exception {
        Path database = bootstrappedDatabase("direct.db");

        try (Connection connection = openObserved(database, SqliteDatabaseSecurity.DEFAULT_BUSY_TIMEOUT_MILLIS)) {
            insertProbeRow(connection);
        }

        assertEquals(
                Map.of(SqliteContentionMetrics.TRANSACTIONS_STARTED, 1L,
                        SqliteContentionMetrics.TRANSACTIONS_COMMITTED, 1L),
                metrics.snapshot().counters());
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
             Connection contender = openObserved(database, CONTENDED_BUSY_TIMEOUT_MILLIS)) {
            try (Statement statement = locker.createStatement()) {
                statement.execute("PRAGMA busy_timeout = " + CONTENDED_BUSY_TIMEOUT_MILLIS);
                statement.execute("BEGIN EXCLUSIVE");
            }

            assertThrows(KnowledgeStoreException.class, () -> insertProbeRow(contender));

            assertEquals(
                    Map.of(SqliteContentionMetrics.TRANSACTIONS_STARTED, 1L,
                            SqliteContentionMetrics.TRANSACTIONS_ROLLED_BACK, 1L,
                            SqliteContentionMetrics.CONTENDED_TRANSACTIONS, 1L),
                    metrics.snapshot().counters(),
                    "a transaction that lost to an exclusive writer is rolled back, attributed to SQLite "
                            + "contention, and never counted as committed");

            try (Statement statement = locker.createStatement()) {
                statement.execute("ROLLBACK");
            }

            insertProbeRow(contender);

            assertEquals(
                    Map.of(SqliteContentionMetrics.TRANSACTIONS_STARTED, 2L,
                            SqliteContentionMetrics.TRANSACTIONS_ROLLED_BACK, 1L,
                            SqliteContentionMetrics.CONTENDED_TRANSACTIONS, 1L,
                            SqliteContentionMetrics.TRANSACTIONS_COMMITTED, 1L),
                    metrics.snapshot().counters(),
                    "the transaction that succeeds once the lock is released is committed, and is not contention");
        }
    }

    /** A business failure inside a transaction is a rollback, never SQLite contention. */
    @Test
    void anOrdinaryTransactionFailureIsNotReportedAsContention() throws Exception {
        Path database = bootstrappedDatabase("business-failure.db");

        try (Connection connection = openObserved(database, SqliteDatabaseSecurity.DEFAULT_BUSY_TIMEOUT_MILLIS)) {
            assertThrows(IllegalStateException.class, () -> SqliteTransactionRunner.runVoid(
                    connection,
                    "deliberate failure",
                    current -> {
                        throw new IllegalStateException("business rule violated");
                    }));
        }

        assertEquals(
                Map.of(SqliteContentionMetrics.TRANSACTIONS_STARTED, 1L,
                        SqliteContentionMetrics.TRANSACTIONS_ROLLED_BACK, 1L),
                metrics.snapshot().counters());
    }

    private Connection openObserved(Path database, int busyTimeoutMillis) throws SQLException {
        return SqliteDatabaseSecurity.openPhysical(database, busyTimeoutMillis, new SqliteContentionMetrics(recorder));
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

    private long counter(String name) {
        return metrics.snapshot().counters().getOrDefault(name, 0L);
    }
}
