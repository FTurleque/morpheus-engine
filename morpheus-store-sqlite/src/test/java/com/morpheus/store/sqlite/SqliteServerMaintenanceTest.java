package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
