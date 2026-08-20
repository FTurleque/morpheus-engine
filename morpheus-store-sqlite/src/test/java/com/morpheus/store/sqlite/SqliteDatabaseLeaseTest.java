package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void nonRegularAccessLockEntryFailsClosed() throws Exception {
        Path database = temp.resolve("morpheus.db");
        Files.createDirectory(temp.resolve("morpheus.db.access.lock"));

        assertThrows(IllegalArgumentException.class, () -> SqliteDatabaseLease.acquireShared(database));
    }
}
