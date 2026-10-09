package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An offline restore replaces the live database and discards it. Whatever the runtime would refuse to open must
 * therefore be refused before the replacement: once the previous database is discarded, a backup that cannot be
 * opened leaves the operator with neither.
 */
class SqliteRestoreLedgerVerificationTest {

    private static final String LIVE_MARKER = "live-marker";

    @TempDir
    Path temp;

    @Test
    void aBackupWhoseLedgerTheRuntimeRefusesIsNotRestoredAndTheLiveDatabaseSurvives() throws Exception {
        Path live = temp.resolve("live.db");
        createDatabase(live);
        markLive(live);
        Path backup = foreignBackup("0".repeat(64));

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> new SqliteServerMaintenance().restoreOffline(backup, live, true));

        assertEquals("backup migration history is not accepted by this runtime for version 1", refusal.getMessage());
        try (var ignored = new SqliteSpecificationKnowledgeStore(live)) {
            // The live database still opens.
        }
        assertTrue(hasLiveMarker(live), "the live database must be the one that was there before the refused restore");
    }

    @Test
    void aBackupWrittenByAnLfCheckoutIsRestoredAndOpens() throws Exception {
        assertRestoredAndOpens(SqliteMigrationLineEndingCompatibilityTest.ledger("\n"));
    }

    @Test
    void aBackupWrittenByACrlfCheckoutIsRestoredAndOpens() throws Exception {
        assertRestoredAndOpens(SqliteMigrationLineEndingCompatibilityTest.ledger("\r\n"));
    }

    private void assertRestoredAndOpens(Map<Integer, String> backupLedger) throws Exception {
        Path live = temp.resolve("live.db");
        createDatabase(live);
        markLive(live);
        Path backup = foreignBackup(backupLedger);

        new SqliteServerMaintenance().restoreOffline(backup, live, true);

        try (var ignored = new SqliteSpecificationKnowledgeStore(live)) {
            // The restored database opens with this runtime.
        }
        assertFalse(hasLiveMarker(live), "the restored database must be the backup, not the previous live database");
    }

    private Path foreignBackup(String versionOneChecksum) throws Exception {
        return foreignBackup(Map.of(1, versionOneChecksum));
    }

    private Path foreignBackup(Map<Integer, String> checksums) throws Exception {
        Path source = temp.resolve("foreign").resolve("source.db");
        Files.createDirectories(source.getParent());
        createDatabase(source);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + source.toAbsolutePath());
             var update = connection.prepareStatement("UPDATE schema_migrations SET checksum = ? WHERE version = ?")) {
            for (Map.Entry<Integer, String> entry : checksums.entrySet()) {
                update.setString(1, entry.getValue());
                update.setInt(2, entry.getKey());
                assertEquals(1, update.executeUpdate());
            }
        }
        Path backup = temp.resolve("backups").resolve("foreign-backup.db");
        Files.createDirectories(backup.getParent());
        Files.copy(source, backup);
        return backup;
    }

    private static void createDatabase(Path database) {
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Opening the store creates and validates the current schema.
        }
    }

    private static void markLive(Path database) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var insert = connection.prepareStatement(
                     "INSERT INTO projects(id, root_scheme, root_value) VALUES (?, 'file', 'live')")) {
            insert.setString(1, LIVE_MARKER);
            insert.executeUpdate();
        }
    }

    private static boolean hasLiveMarker(Path database) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var query = connection.prepareStatement("SELECT COUNT(*) FROM projects WHERE id = ?")) {
            query.setString(1, LIVE_MARKER);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() && rows.getInt(1) == 1;
            }
        }
    }
}
