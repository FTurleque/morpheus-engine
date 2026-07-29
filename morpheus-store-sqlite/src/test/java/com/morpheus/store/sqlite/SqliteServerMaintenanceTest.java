package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteServerMaintenanceTest {
    @TempDir
    Path temp;

    @Test
    void backupIsIntegrityCheckedVersionedAndRestorableOffline() throws Exception {
        Path database = temp.resolve("morpheus.db");
        try (SqliteSpecificationKnowledgeStore ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Opening the store creates and validates the current schema.
        }

        SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
        SqliteServerMaintenance.BackupVerification backup =
                maintenance.createBackup(database, temp.resolve("backups"));

        assertTrue(Files.isRegularFile(backup.path()));
        assertTrue(backup.integrityOk());
        assertEquals(SqliteServerMaintenance.SUPPORTED_SCHEMA_VERSION, backup.schemaVersion());
        assertEquals(backup.sha256(), maintenance.verify(backup.path()).sha256());

        Path restored = temp.resolve("restored.db");
        SqliteServerMaintenance.BackupVerification restoredView =
                maintenance.restoreOffline(backup.path(), restored, true);
        assertTrue(restoredView.integrityOk());
        assertEquals(backup.schemaVersion(), restoredView.schemaVersion());
        assertEquals(backup.sha256(), restoredView.sha256());
    }

    @Test
    void restoreRequiresConfirmationAndFailsWhileServerLeaseIsHeld() {
        Path database = temp.resolve("morpheus.db");
        try (SqliteSpecificationKnowledgeStore ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // initialize
        }
        SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
        SqliteServerMaintenance.BackupVerification backup =
                maintenance.createBackup(database, temp.resolve("backups"));

        assertThrows(IllegalArgumentException.class,
                () -> maintenance.restoreOffline(backup.path(), database, false));
        try (SqliteServerMaintenance.ServerLease ignored = maintenance.acquireServerLease(database)) {
            assertThrows(IllegalStateException.class,
                    () -> maintenance.restoreOffline(backup.path(), database, true));
        }
    }

    @Test
    void futureSchemaBackupIsRejectedInsteadOfBeingDowngraded() throws Exception {
        Path database = temp.resolve("morpheus.db");
        try (SqliteSpecificationKnowledgeStore ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // initialize
        }
        SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
        Path future = temp.resolve("future.db");
        Files.copy(maintenance.createBackup(database, temp.resolve("backups")).path(), future,
                StandardCopyOption.REPLACE_EXISTING);

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + future);
             var statement = connection.prepareStatement(
                     "INSERT INTO schema_migrations(version, name, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, SqliteServerMaintenance.SUPPORTED_SCHEMA_VERSION + 1);
            statement.setString(2, "future-test");
            statement.setString(3, "future-checksum");
            statement.setString(4, "2026-07-29T00:00:00Z");
            statement.executeUpdate();
        }

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> maintenance.verify(future));
        assertTrue(failure.getMessage().contains("newer than supported"));
    }
}
