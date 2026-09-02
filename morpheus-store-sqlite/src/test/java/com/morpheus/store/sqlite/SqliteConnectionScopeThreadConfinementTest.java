package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the confinement the scope's design depends on.
 *
 * <p>The scope shares one physical SQLite connection between every store adapter on the owning thread. That is
 * only safe while that thread is the only user: a logical connection handed to another thread would drive the
 * same handle concurrently, interleaving statements and transaction state. The proxy therefore refuses database
 * access from anywhere else, and the owning thread keeps working normally afterwards.</p>
 */
class SqliteConnectionScopeThreadConfinementTest {
    @TempDir
    Path tempDir;

    @Test
    void aLogicalConnectionRefusesDatabaseAccessFromAnotherThread() throws Exception {
        Path database = tempDir.resolve("confinement.db");
        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database)) {
            Connection borrowed = SqliteConnectionScope.borrowIfActive(database, busyTimeout());
            assertNotNull(borrowed, "an active scope must hand out a logical connection");

            // A single throwing call, so the refusal cannot be attributed to the wrong one: the proxy stops the
            // foreign thread at createStatement, before any statement exists to execute.
            SQLException refused = onAnotherThread(
                    () -> assertThrows(SQLException.class, borrowed::createStatement));

            assertTrue(
                    refused.getMessage().contains("confined to the thread that opened its scope"),
                    () -> "expected a confinement refusal, got: " + refused.getMessage());

            // The owning thread is unaffected: the refusal must not have disturbed the shared connection.
            try (Statement statement = borrowed.createStatement()) {
                assertTrue(statement.execute("SELECT 1"));
            }
            assertEquals(1, scope.logicalConnectionsBorrowed());
        }
    }

    @Test
    void closingALogicalConnectionFromAnotherThreadIsRefusedAndLeavesItUsable() throws Exception {
        Path database = tempDir.resolve("foreign-close.db");
        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database)) {
            Connection borrowed = SqliteConnectionScope.borrowIfActive(database, busyTimeout());
            assertNotNull(borrowed);

            SQLException refused = onAnotherThread(() ->
                    assertThrows(SQLException.class, borrowed::close));
            assertTrue(refused.getMessage().contains("confined to the thread that opened its scope"));

            // A foreign close must not have marked the connection closed underneath its owner.
            try (Statement statement = borrowed.createStatement()) {
                assertTrue(statement.execute("SELECT 1"));
            }
            assertDoesNotThrow(borrowed::close);
            assertEquals(1, scope.logicalConnectionsBorrowed());
        }
    }

    /** Identity is not database access, so it stays available anywhere and needs no owning thread. */
    @Test
    void identityOperationsRemainAvailableFromAnyThread() throws Exception {
        Path database = tempDir.resolve("identity.db");
        try (SqliteConnectionScope ignored = SqliteConnectionScope.open(database)) {
            Connection borrowed = SqliteConnectionScope.borrowIfActive(database, busyTimeout());
            Connection sibling = SqliteConnectionScope.borrowIfActive(database, busyTimeout());
            assertNotNull(borrowed);
            assertNotNull(sibling);

            // Two distinct logical connections over the same scope. Comparing them exercises the identity path
            // the proxy implements, from the foreign thread, instead of asserting a tautology.
            String rendered = onAnotherThread(() -> {
                assertNotEquals(borrowed, sibling);
                assertEquals(System.identityHashCode(borrowed), borrowed.hashCode());
                return borrowed.toString();
            });

            assertTrue(rendered.startsWith("ScopedSqliteConnection["), rendered);
        }
    }

    @Test
    void theScopeStillClosesNormallyOnItsOwningThreadAfterAForeignAttempt() throws Exception {
        Path database = tempDir.resolve("still-closeable.db");
        SqliteConnectionScope scope = SqliteConnectionScope.open(database);
        Connection borrowed = SqliteConnectionScope.borrowIfActive(database, busyTimeout());
        assertNotNull(borrowed);

        onAnotherThread(() -> assertThrows(SQLException.class, borrowed::createStatement));

        assertDoesNotThrow(scope::close);
        // A second close on the owning thread stays a no-op.
        assertDoesNotThrow(scope::close);
    }

    private static int busyTimeout() {
        return SqliteDatabaseSecurity.DEFAULT_BUSY_TIMEOUT_MILLIS;
    }

    private static <T> T onAnotherThread(Callable<T> action) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "foreign-scope-thread");
            thread.setDaemon(true);
            return thread;
        });
        try {
            return executor.submit(action).get();
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            assertInstanceOf(Exception.class, cause, "unexpected throwable from the foreign thread");
            throw (Exception) cause;
        } finally {
            executor.shutdownNow();
        }
    }
}
