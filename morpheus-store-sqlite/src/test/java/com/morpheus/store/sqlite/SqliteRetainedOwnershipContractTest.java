package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens to a physical handle whose close failed while an open was already unwinding.
 *
 * <p>Refusing to give the lease back is right: the handle may still be alive, and an offline restore replaces
 * the database file and its sidecars. But the handle and its lease were then unreachable — nothing could ever
 * finish the release, so the database stayed reserved for the life of the process, which is a leak wearing the
 * costume of a safety property.</p>
 *
 * <p>They are retained together instead. Acquiring the exclusive lease attempts the release first, so recovery
 * is a controlled retry that grants nothing unless a close actually succeeds.</p>
 */
class SqliteRetainedOwnershipContractTest {

    @TempDir
    Path temp;

    @Test
    void aHandleWhoseCloseFailedKeepsMaintenanceOutUntilARetrySucceeds() {
        Path database = temp.resolve("retained.db").toAbsolutePath().normalize();
        SQLException closeFailure = new SQLException("injected physical close failure");
        AtomicInteger closeAttempts = new AtomicInteger();

        SqliteDatabaseLease.Lease lease = SqliteDatabaseLease.acquireShared(database);
        SqliteDatabaseLease.retain(
                database, failingFirstClose(closeAttempts, closeFailure), lease, closeFailure);

        assertEquals(1, SqliteDatabaseLease.retainedCount(database));
        assertSame(closeFailure, SqliteDatabaseLease.lastRetainedFailure(database).orElseThrow());

        // First attempt: acquiring the exclusive lease retries the release, the close fails again, and the lease
        // is still held -- so maintenance stays refused rather than running over a possibly live handle.
        IllegalStateException refused = assertThrows(
                IllegalStateException.class, () -> SqliteDatabaseLease.acquireExclusive(database));
        assertTrue(refused.getMessage().contains("still open in another MORPHEUS operation"), refused.getMessage());
        assertEquals(1, closeAttempts.get(), "the retry must have actually attempted the close");
        assertEquals(1, SqliteDatabaseLease.retainedCount(database), "a failed retry keeps the handle retained");

        // Second attempt: the close succeeds, so the lease goes back and maintenance may proceed.
        SqliteDatabaseLease.acquireExclusive(database).close();
        assertEquals(2, closeAttempts.get());
        assertEquals(0, SqliteDatabaseLease.retainedCount(database), "a resolved handle is forgotten");

        // Repeatable: nothing was double-released, and the lease counters stayed coherent.
        SqliteDatabaseLease.acquireExclusive(database).close();
        assertEquals(2, closeAttempts.get(), "a resolved handle must not be closed again");
    }

    /**
     * The whole path, driven by a real open that fails after the driver has connected: a file that is not a
     * database is rejected by the first PRAGMA. Here the physical close succeeds, so the lease goes straight
     * back — and the failure the caller sees is the one that broke the open, not anything from the cleanup.
     */
    @Test
    void anOpenThatFailsAfterConnectingReportsItsOwnFailureAndReturnsTheLease() throws Exception {
        Path database = temp.resolve("not-a-database.db").toAbsolutePath().normalize();
        java.nio.file.Files.writeString(database, "this is definitely not a sqlite database");

        SQLException failure = assertThrows(SQLException.class, () -> SqliteDatabaseSecurity.openPhysical(
                database, SqliteDatabaseSecurity.DEFAULT_BUSY_TIMEOUT_MILLIS));
        assertTrue(failure.getMessage().contains("not a database"), failure.getMessage());

        assertEquals(0, SqliteDatabaseLease.retainedCount(database),
                "a close that succeeded leaves nothing retained");
        SqliteDatabaseLease.acquireExclusive(database).close();
    }

    /** Resolving a database with nothing retained is a no-op, so the retry is safe to attempt unconditionally. */
    @Test
    void resolvingADatabaseWithNothingRetainedChangesNothing() {
        Path database = temp.resolve("nothing-retained.db").toAbsolutePath().normalize();

        SqliteDatabaseLease.resolveRetained(database);

        assertEquals(0, SqliteDatabaseLease.retainedCount(database));
        assertTrue(SqliteDatabaseLease.lastRetainedFailure(database).isEmpty());
        SqliteDatabaseLease.acquireExclusive(database).close();
    }

    /** Refuses the first close the way a driver would, then behaves, so the retry contract is observable. */
    private static Connection failingFirstClose(AtomicInteger attempts, SQLException failure) {
        return (Connection) Proxy.newProxyInstance(
                SqliteRetainedOwnershipContractTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) {
                        if (attempts.incrementAndGet() == 1) {
                            throw failure;
                        }
                        return null;
                    }
                    if (method.getName().equals("isClosed")) {
                        return attempts.get() > 1;
                    }
                    throw new SQLException("unexpected call on the injected connection: " + method.getName());
                });
    }
}
