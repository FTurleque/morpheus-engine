package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteTransactionRunnerTest {

    @Test
    void primaryRuntimeFailureSurvivesRollbackAndCleanupFailures() {
        Connection connection = connection(true, true, true);
        IllegalStateException primary = new IllegalStateException("primary");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                SqliteTransactionRunner.runVoid(connection, "store failed", ignored -> {
                    throw primary;
                }));

        assertEquals(primary, thrown);
        assertEquals(2, thrown.getSuppressed().length);
        assertTrue(thrown.getSuppressed()[0].getMessage().contains("rollback"));
        assertTrue(thrown.getSuppressed()[1].getMessage().contains("cleanup"));
    }

    @Test
    void primaryErrorSurvivesRollbackAndCleanupFailures() {
        Connection connection = connection(true, true, true);
        AssertionError primary = new AssertionError("fatal-primary");

        AssertionError thrown = assertThrows(AssertionError.class, () ->
                SqliteTransactionRunner.runVoid(connection, "store failed", ignored -> {
                    throw primary;
                }));

        assertEquals(primary, thrown);
        assertEquals(2, thrown.getSuppressed().length);
        assertTrue(thrown.getSuppressed()[0].getMessage().contains("rollback"));
        assertTrue(thrown.getSuppressed()[1].getMessage().contains("cleanup"));
    }

    @Test
    void sqlFailureIsWrappedAndRollbackFailureIsSuppressed() {
        Connection connection = connection(true, true, false);

        KnowledgeStoreException thrown = assertThrows(KnowledgeStoreException.class, () ->
                SqliteTransactionRunner.runVoid(connection, "store failed", ignored -> {
                    throw new SQLException("sql-primary");
                }));

        assertEquals("store failed", thrown.getMessage());
        assertInstanceOf(SQLException.class, thrown.getCause());
        assertEquals(1, thrown.getSuppressed().length);
    }

    @Test
    void rejectsCallerOwnedTransactionWithoutCommittingOrRollingBackIt() {
        AtomicBoolean commitCalled = new AtomicBoolean();
        AtomicBoolean rollbackCalled = new AtomicBoolean();
        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> false;
                    case "commit" -> {
                        commitCalled.set(true);
                        yield null;
                    }
                    case "rollback" -> {
                        rollbackCalled.set(true);
                        yield null;
                    }
                    case "close", "setAutoCommit" -> null;
                    case "isClosed" -> false;
                    default -> defaultValue(method.getReturnType());
                });

        KnowledgeStoreException thrown = assertThrows(
                KnowledgeStoreException.class,
                () -> SqliteTransactionRunner.runVoid(connection, "store failed", ignored -> { }));

        assertTrue(thrown.getMessage().contains("caller-owned transactions are not supported"));
        assertFalse(commitCalled.get());
        assertFalse(rollbackCalled.get());
    }

    @Test
    void cleanupFailureAfterSuccessfulCommitKeepsDurableMutationSuccessfulAndQuarantinesDirectConnection() {
        AtomicBoolean committed = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean transactionModeSet = new AtomicBoolean();
        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> true;
                    case "setAutoCommit" -> {
                        boolean value = (boolean) args[0];
                        if (!value) {
                            transactionModeSet.set(true);
                            yield null;
                        }
                        if (transactionModeSet.get()) throw new SQLException("cleanup failed");
                        yield null;
                    }
                    case "commit" -> {
                        committed.set(true);
                        yield null;
                    }
                    case "rollback" -> null;
                    case "close" -> {
                        closed.set(true);
                        yield null;
                    }
                    case "isClosed" -> closed.get();
                    default -> defaultValue(method.getReturnType());
                });

        String result = assertDoesNotThrow(() -> SqliteTransactionRunner.run(connection, "store failed", ignored -> "committed"));

        assertEquals("committed", result);
        assertTrue(committed.get());
        assertTrue(closed.get());
        KnowledgeStoreException quarantined = assertThrows(KnowledgeStoreException.class, () ->
                SqliteTransactionRunner.runVoid(connection, "must not execute", ignored -> { }));
        assertTrue(quarantined.getMessage().contains("quarantined"));
    }

    @Test
    void transientCleanupFailureAfterCommitIsRecoveredByRetryWithoutQuarantine() {
        AtomicBoolean transactionModeSet = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        int[] cleanupAttempts = {0};
        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> true;
                    case "setAutoCommit" -> {
                        boolean value = (boolean) args[0];
                        if (!value) {
                            transactionModeSet.set(true);
                            yield null;
                        }
                        if (transactionModeSet.get() && cleanupAttempts[0]++ == 0) {
                            throw new SQLException("transient cleanup failed");
                        }
                        yield null;
                    }
                    case "commit", "rollback" -> null;
                    case "close" -> {
                        closed.set(true);
                        yield null;
                    }
                    case "isClosed" -> closed.get();
                    default -> defaultValue(method.getReturnType());
                });

        assertEquals("ok", SqliteTransactionRunner.run(connection, "failed", ignored -> "ok"));
        assertEquals(2, cleanupAttempts[0]);
        assertFalse(closed.get());
        assertDoesNotThrow(() -> SqliteTransactionRunner.runVoid(connection, "second", ignored -> { }));
    }

    @Test
    void primaryBusinessFailureSurvivesSuccessfulRollback() {
        Connection connection = connection(true, false, false);
        IllegalArgumentException primary = new IllegalArgumentException("business-primary");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                SqliteTransactionRunner.runVoid(connection, "store failed", ignored -> {
                    throw primary;
                }));

        assertEquals(primary, thrown);
        assertEquals(0, thrown.getSuppressed().length);
    }

    @Test
    void successfulTransactionCommitsAndRestoresAutoCommit() {
        AtomicBoolean committed = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();
        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> true;
                    case "setAutoCommit" -> {
                        if (Boolean.TRUE.equals(args[0])) restored.set(true);
                        yield null;
                    }
                    case "commit" -> {
                        committed.set(true);
                        yield null;
                    }
                    case "rollback", "close" -> null;
                    case "isClosed" -> false;
                    default -> defaultValue(method.getReturnType());
                });

        String result = SqliteTransactionRunner.run(connection, "failed", ignored -> "ok");
        assertEquals("ok", result);
        assertTrue(committed.get());
        assertTrue(restored.get());
    }

    private Connection connection(boolean initialAutoCommit, boolean rollbackFails, boolean cleanupFails) {
        AtomicBoolean transactionModeSet = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> initialAutoCommit;
                    case "setAutoCommit" -> {
                        boolean value = (boolean) args[0];
                        if (!value) {
                            transactionModeSet.set(true);
                            yield null;
                        }
                        if (cleanupFails && transactionModeSet.get()) throw new SQLException("cleanup failed");
                        yield null;
                    }
                    case "rollback" -> {
                        if (rollbackFails) throw new SQLException("rollback failed");
                        yield null;
                    }
                    case "commit", "close" -> null;
                    case "isClosed" -> false;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }
}
