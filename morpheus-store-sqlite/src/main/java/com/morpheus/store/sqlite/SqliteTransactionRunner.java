package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Executes one owned SQLite transaction while preserving the primary failure across rollback/cleanup errors. */
final class SqliteTransactionRunner {
    private static final System.Logger LOGGER = System.getLogger(SqliteTransactionRunner.class.getName());
    private static final Map<Connection, Throwable> QUARANTINED = Collections.synchronizedMap(new WeakHashMap<>());

    private SqliteTransactionRunner() {
    }

    static <T> T run(Connection connection, String failureMessage, SqlWork<T> work) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(failureMessage, "failureMessage");
        Objects.requireNonNull(work, "work");
        rejectQuarantined(connection);

        final boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot inspect SQLite auto-commit mode", failure);
        }
        if (!previousAutoCommit) {
            throw new KnowledgeStoreException(
                    "SQLite transaction runner requires auto-commit mode; nested or caller-owned transactions are not supported");
        }

        SqliteContentionMetrics.transactionStarted();
        long startedNanos = System.nanoTime();
        try {
            connection.setAutoCommit(false);
            T result = work.run(connection);
            connection.commit();
            SqliteContentionMetrics.transactionCommitted(System.nanoTime() - startedNanos);
            restoreAfterSuccessfulCommit(connection, previousAutoCommit);
            return result;
        } catch (SQLException failure) {
            KnowledgeStoreException wrapped = new KnowledgeStoreException(failureMessage, failure);
            SqliteContentionMetrics.transactionRolledBack(System.nanoTime() - startedNanos, wrapped);
            rollbackSuppressing(connection, wrapped);
            restoreAfterFailure(connection, previousAutoCommit, wrapped);
            throw wrapped;
        } catch (RuntimeException failure) {
            SqliteContentionMetrics.transactionRolledBack(System.nanoTime() - startedNanos, failure);
            rollbackSuppressing(connection, failure);
            restoreAfterFailure(connection, previousAutoCommit, failure);
            throw failure;
        } catch (Error failure) {
            SqliteContentionMetrics.transactionRolledBack(System.nanoTime() - startedNanos, failure);
            rollbackSuppressing(connection, failure);
            restoreAfterFailure(connection, previousAutoCommit, failure);
            throw failure;
        }
    }

    static void runVoid(Connection connection, String failureMessage, SqlVoidWork work) {
        run(connection, failureMessage, current -> {
            work.run(current);
            return null;
        });
    }

    private static void rollbackSuppressing(Connection connection, Throwable primary) {
        try {
            connection.rollback();
        } catch (SQLException | RuntimeException | Error rollbackFailure) {
            suppress(primary, rollbackFailure);
        }
    }

    private static void restoreAfterFailure(Connection connection, boolean previousAutoCommit, Throwable primary) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException | RuntimeException | Error cleanupFailure) {
            suppress(primary, cleanupFailure);
        }
    }

    /**
     * A cleanup failure after commit must never turn a durable mutation into an apparent business failure.
     * Retry the JDBC cleanup once. If the connection belongs to an operation scope, replace its physical
     * connection so every existing logical proxy remains usable. Otherwise quarantine/close the direct
     * connection and report the already committed operation as successful; a later transaction is rejected.
     */
    private static void restoreAfterSuccessfulCommit(Connection connection, boolean previousAutoCommit) {
        Throwable cleanupFailure;
        try {
            connection.setAutoCommit(previousAutoCommit);
            return;
        } catch (SQLException | RuntimeException | Error firstFailure) {
            cleanupFailure = firstFailure;
        }

        try {
            connection.setAutoCommit(previousAutoCommit);
            LOGGER.log(System.Logger.Level.WARNING,
                    "SQLite auto-commit restoration failed after commit but succeeded on retry; mutation remains committed",
                    cleanupFailure);
            return;
        } catch (SQLException | RuntimeException | Error retryFailure) {
            suppress(cleanupFailure, retryFailure);
        }

        if (SqliteConnectionScope.recoverAfterCommittedCleanupFailure(connection, cleanupFailure)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "SQLite connection scope recovered after post-commit cleanup failure; mutation remains committed",
                    cleanupFailure);
            return;
        }

        QUARANTINED.put(connection, cleanupFailure);
        try {
            connection.close();
        } catch (SQLException | RuntimeException | Error closeFailure) {
            suppress(cleanupFailure, closeFailure);
        }
        LOGGER.log(System.Logger.Level.ERROR,
                "SQLite direct connection quarantined after post-commit cleanup failure; mutation remains committed",
                cleanupFailure);
    }

    private static void rejectQuarantined(Connection connection) {
        Throwable failure = QUARANTINED.get(connection);
        if (failure != null) {
            throw new KnowledgeStoreException(
                    "SQLite connection is quarantined after a previously committed mutation cleanup failure",
                    failure);
        }
    }

    private static void suppress(Throwable primary, Throwable secondary) {
        if (primary != secondary) primary.addSuppressed(secondary);
    }

    @FunctionalInterface
    interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    interface SqlVoidWork {
        void run(Connection connection) throws SQLException;
    }
}
