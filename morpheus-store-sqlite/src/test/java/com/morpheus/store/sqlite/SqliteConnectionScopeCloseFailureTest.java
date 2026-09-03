package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what a scope may claim when the driver refuses to release its physical connection.
 *
 * <p>The scope detached from its thread, marked itself closed and decremented the active-connection gauge before
 * it knew whether the JDBC close had worked. A failed close therefore reported a connection the process still
 * held as released, and the owning thread could not try again: the next call returned immediately.</p>
 */
class SqliteConnectionScopeCloseFailureTest {
    @TempDir
    Path temp;

    @Test
    void aFailedCloseKeepsTheScopeOpenRefusesFurtherWorkAndStaysRetryable() throws Exception {
        Path database = temp.resolve("close-failure.db");
        SQLException injected = new SQLException("injected physical close failure");
        AtomicInteger closeAttempts = new AtomicInteger();
        Connection physical = SqliteDatabaseSecurity.openPhysical(
                database, SqliteDatabaseSecurity.DEFAULT_BUSY_TIMEOUT_MILLIS);

        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();
        SqliteConnectionScope scope = SqliteConnectionScope.adopt(
                database,
                SqliteDatabaseSecurity.DEFAULT_BUSY_TIMEOUT_MILLIS,
                failingFirstClose(physical, closeAttempts, injected));

        SqliteConnectionScope.Diagnostics opened = SqliteConnectionScope.diagnostics();
        assertEquals(before.active() + 1, opened.active(), "adopting the connection must count it as active");

        IllegalStateException failure = assertThrows(IllegalStateException.class, scope::close);
        assertEquals("cannot close SQLite operation scope", failure.getMessage());
        assertSame(injected, failure.getCause(), "the driver failure must survive as the cause");
        assertEquals(1, closeAttempts.get());

        SqliteConnectionScope.Diagnostics afterFailure = SqliteConnectionScope.diagnostics();
        assertEquals(opened.active(), afterFailure.active(),
                "a connection the driver refused to release is still held, so the gauge must not drop");
        assertEquals(before.closed(), afterFailure.closed(),
                "a release that failed must not be counted as a release that happened");

        SQLException refused = assertThrows(SQLException.class, () -> SqliteDatabaseSecurity.open(database));
        assertTrue(refused.getMessage().contains("quarantined"),
                () -> "a scope whose cleanup failed must refuse new logical connections: " + refused.getMessage());
        assertSame(injected, refused.getCause());

        AtomicReference<Throwable> fromAnotherThread = new AtomicReference<>();
        Thread stranger = new Thread(() -> {
            try {
                scope.close();
            } catch (Throwable thrown) {
                fromAnotherThread.set(thrown);
            }
        }, "not-the-sqlite-scope-owner");
        stranger.start();
        stranger.join();
        assertInstanceOf(IllegalStateException.class, fromAnotherThread.get());
        assertEquals("SQLite connection scope must close on its owning thread",
                fromAnotherThread.get().getMessage());
        assertEquals(1, closeAttempts.get(), "a stranger must not get to retry the owner's cleanup");

        scope.close();
        assertEquals(2, closeAttempts.get(), "the owning thread must be able to retry the release");

        SqliteConnectionScope.Diagnostics afterRetry = SqliteConnectionScope.diagnostics();
        assertEquals(before.active(), afterRetry.active());
        assertEquals(before.closed() + 1, afterRetry.closed());

        scope.close();
        assertEquals(2, closeAttempts.get(), "a close after a successful one must do nothing");
        assertEquals(afterRetry.active(), SqliteConnectionScope.diagnostics().active(),
                "the released connection must be counted once, not once per close call");
    }

    /** Refuses the first release the way a driver would, then behaves, so the retry contract is observable. */
    private static Connection failingFirstClose(
            Connection delegate, AtomicInteger attempts, SQLException failure) {
        return (Connection) Proxy.newProxyInstance(
                SqliteConnectionScopeCloseFailureTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close") && attempts.incrementAndGet() == 1) {
                        throw failure;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException invoked) {
                        throw invoked.getCause();
                    }
                });
    }
}
