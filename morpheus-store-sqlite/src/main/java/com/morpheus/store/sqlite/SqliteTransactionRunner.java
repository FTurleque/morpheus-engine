package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Executes one SQLite transaction while preserving the primary failure across rollback/cleanup errors. */
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

        RuntimeException primary = null;
        try {
            connection.setAutoCommit(false);
            T result = work.run(connection);
            connection.commit();
            return result;
        } catch (SQLException failure) {
            primary = new KnowledgeStoreException(failureMessage, failure);
            rollbackSuppressing(connection, primary);
            throw primary;
        } catch (RuntimeException failure) {
            primary = failure;
            rollbackSuppressing(connection, primary);
            throw primary;
        } finally {
            restoreAutoCommit(connection, previousAutoCommit, primary);
        }
    }

    static void runVoid(Connection connection, String failureMessage, SqlVoidWork work) {
        run(connection, failureMessage, current -> {
            work.run(current);
            return null;
        });
    }

    private static void rollbackSuppressing(Connection connection, RuntimeException primary) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            primary.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(
            Connection connection,
            boolean previousAutoCommit,
            RuntimeException primary) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException cleanupFailure) {
            if (primary != null) {
                primary.addSuppressed(cleanupFailure);
                return;
            }
            throw new KnowledgeStoreException("Cannot restore SQLite auto-commit mode", cleanupFailure);
        }
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
