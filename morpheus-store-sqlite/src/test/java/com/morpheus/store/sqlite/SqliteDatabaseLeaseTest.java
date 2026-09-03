package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDatabaseLeaseTest {
    @TempDir
    Path temp;

    @Test
    void sharedConnectionsRefCountAndExclusiveMaintenanceWaitsForLastClose() throws Exception {
        Path database = temp.resolve("morpheus.db");
        var first = SqliteDatabaseSecurity.open(database);
        var second = SqliteDatabaseSecurity.open(database);
        try {
            assertFalse(first.isClosed());
            assertFalse(second.isClosed());
            assertTrue(first.toString().startsWith("LeasedSqliteConnection["));
            assertThrows(IllegalStateException.class, () -> SqliteDatabaseLease.acquireExclusive(database));

            first.close();
            assertTrue(first.isClosed());
            assertThrows(IllegalStateException.class, () -> SqliteDatabaseLease.acquireExclusive(database));

            second.close();
            try (SqliteDatabaseLease.Lease exclusive = SqliteDatabaseLease.acquireExclusive(database)) {
                assertThrows(IllegalStateException.class, () -> SqliteDatabaseSecurity.open(database));
            }

            try (var reopened = SqliteDatabaseSecurity.open(database)) {
                assertFalse(reopened.isClosed());
            }
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void operatingSystemExclusiveLockBlocksSharedMorpheusLease() throws Exception {
        Path database = temp.resolve("morpheus.db").toAbsolutePath().normalize();
        Path lockPath = database.resolveSibling(database.getFileName() + ".access.lock");
        Files.createFile(lockPath);

        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            assertThrows(IllegalStateException.class, () -> SqliteDatabaseLease.acquireShared(database));
        }
    }

    @Test
    void operatingSystemSharedLockBlocksExclusiveMaintenanceLease() throws Exception {
        Path database = temp.resolve("morpheus.db").toAbsolutePath().normalize();
        Path lockPath = database.resolveSibling(database.getFileName() + ".access.lock");
        Files.createFile(lockPath);

        try (FileChannel channel = FileChannel.open(
                lockPath, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock(0L, Long.MAX_VALUE, true)) {
            assertThrows(IllegalStateException.class, () -> SqliteDatabaseLease.acquireExclusive(database));
        }
    }

    /**
     * The lease is what keeps offline maintenance out while a connection is open, so it must be released even
     * when the driver refuses to close that connection. It was released from a finally block, which did release
     * it but let its own outcome replace the driver failure, and the guarded connection had already recorded
     * itself as released before either call ran -- so a retry after a failed release did nothing at all.
     */
    @Test
    void aDriverThatRefusesToCloseStillReleasesTheLeaseAndLeavesTheReleaseRetryable() throws Exception {
        Path database = temp.resolve("guarded.db").toAbsolutePath().normalize();
        SQLException injected = new SQLException("injected driver close failure");
        AtomicInteger closeAttempts = new AtomicInteger();
        SqliteDatabaseLease.Lease lease = SqliteDatabaseLease.acquireShared(database);
        Connection guarded = SqliteDatabaseLease.guard(failingFirstClose(closeAttempts, injected), lease);

        SQLException refused = assertThrows(SQLException.class, guarded::close);
        assertSame(injected, refused, "the driver failure must not be replaced by the lease release outcome");
        assertEquals(1, closeAttempts.get());

        // Acquiring the exclusive lease is refused while any shared reference is outstanding, so taking it here
        // is what proves the failed close still gave the shared reference back.
        SqliteDatabaseLease.acquireExclusive(database).close();

        assertTrue(guarded.isClosed(), "a connection whose close failed is unusable either way");
        assertThrows(SQLException.class, guarded::createStatement);

        guarded.close();
        assertEquals(2, closeAttempts.get(), "a release that failed must stay retryable");

        guarded.close();
        assertEquals(2, closeAttempts.get(), "a release that succeeded must not run again");
    }

    /** Refuses the first close the way a driver would, then behaves, so the retry contract is observable. */
    private static Connection failingFirstClose(AtomicInteger attempts, SQLException failure) {
        return (Connection) Proxy.newProxyInstance(
                SqliteDatabaseLeaseTest.class.getClassLoader(),
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

    @Test
    void nonRegularAccessLockEntryFailsClosed() throws Exception {
        Path database = temp.resolve("morpheus.db");
        Files.createDirectory(temp.resolve("morpheus.db.access.lock"));

        assertThrows(IllegalArgumentException.class, () -> SqliteDatabaseLease.acquireShared(database));
    }
}
