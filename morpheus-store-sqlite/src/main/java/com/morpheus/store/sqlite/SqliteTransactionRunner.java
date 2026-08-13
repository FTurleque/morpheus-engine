package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Executes one owned SQLite transaction while preserving the primary failure across rollback/cleanup errors. */
final class SqliteTransactionRunner {
    private SqliteTransactionRunner() {
    }

    static <T> T run(Connection connection, String failureMessage, SqlWork<T> work) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(failureMessage, "failureMessage");
        Objects.requireNonNull(work, "work");

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

        Throwable primary = null;
        try {
            connection.setAutoCommit(false);
            T result = work.run(connection);
            connection.commit();
            return result;
        } catch (SQLException failure) {
            KnowledgeStoreException wrapped = new KnowledgeStoreException(failureMessage, failure);
            primary = wrapped;
            rollbackSuppressing(connection, wrapped);
            throw wrapped;
        } catch (RuntimeException failure) {
            primary = failure;
            rollbackSuppressing(connection, failure);
            throw failure;
        } catch (Error failure) {
            primary = failure;
            rollbackSuppressing(connection, failure);
            throw failure;
        } finally {
            restoreAutoCommit(connection, true, primary);
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

    private static void restoreAutoCommit(
            Connection connection,
            boolean previousAutoCommit,
            Throwable primary) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException | RuntimeException | Error cleanupFailure) {
            if (primary != null) {
                suppress(primary, cleanupFailure);
                return;
            }
            throw new SqliteCommittedTransactionException(
                    "SQLite commit succeeded but restoring auto-commit failed; the mutation is committed and must not be retried",
                    cleanupFailure);
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
