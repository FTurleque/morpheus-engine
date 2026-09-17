package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An offline restore that fails must leave the database it was about to replace exactly as it found it.
 *
 * <p>The database is only half of that state. SQLite runs here in {@code PERSIST} journal mode, where a journal
 * file outlives the transaction that wrote it and is replayed on the next open when its header says it is hot.
 * Deleting the journal before the replacement therefore discards the one thing that could still recover the old
 * database -- and a replacement that then fails leaves an operator with a database and no way back.</p>
 *
 * <p>The opposite order is not the answer: it would let the restored database sit, however briefly, beside the
 * previous database's journal under the name SQLite looks for. The old database and its journal move together,
 * out of the way, and come back together if anything fails.</p>
 */
class SqliteRestoreQuarantineOrderingTest {
    private static final String HOT_JOURNAL = "journal left behind by an earlier crash";

    @TempDir
    Path temp;

    @Test
    void aFailedReplacementLeavesTheDatabaseAndItsJournalRecoverable() throws Exception {
        Path database = temp.resolve("morpheus.db");
        try (SqliteSpecificationKnowledgeStore ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Opening the store creates and validates the current schema.
        }
        SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
        SqliteServerMaintenance.BackupVerification backup =
                maintenance.createBackup(database, temp.resolve("backups"));

        Path journal = temp.resolve("morpheus.db-journal");
        Files.writeString(journal, HOT_JOURNAL);
        byte[] databaseBefore = Files.readAllBytes(database);

        KnowledgeStoreException failure = assertThrows(KnowledgeStoreException.class,
                () -> maintenance.restoreOffline(backup.path(), database, true, (from, to, options) -> {
                    throw new IOException("replacement refused by the filesystem");
                }));

        assertTrue(failure.getMessage().contains("Cannot restore SQLite server backup"));
        assertTrue(Files.isRegularFile(database), "a failed restore must leave the previous database in place");
        assertArrayEquals(databaseBefore, Files.readAllBytes(database),
                "a failed restore must not have altered the previous database");
        assertTrue(Files.isRegularFile(journal),
                "the previous database's journal is what recovers it after a crash; a failed restore that "
                        + "destroyed it would leave the database in place and unrecoverable");
        assertEquals(HOT_JOURNAL, Files.readString(journal),
                "the journal must come back with its contents, not merely with its name");
    }

    @Test
    void aFailedReplacementLeavesNoQuarantineBehind() throws Exception {
        Path database = temp.resolve("morpheus.db");
        try (SqliteSpecificationKnowledgeStore ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Opening the store creates and validates the current schema.
        }
        SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
        SqliteServerMaintenance.BackupVerification backup =
                maintenance.createBackup(database, temp.resolve("backups"));
        Files.writeString(temp.resolve("morpheus.db-journal"), HOT_JOURNAL);

        assertThrows(KnowledgeStoreException.class,
                () -> maintenance.restoreOffline(backup.path(), database, true, (from, to, options) -> {
                    throw new IOException("replacement refused by the filesystem");
                }));

        assertEquals(List.of(), strayEntries(temp),
                "rolling the quarantine back must restore the original names, not leave a second copy");
    }

    @Test
    void aSuccessfulRestoreDiscardsTheQuarantineAndLeavesNoOrphanSidecar() throws Exception {
        Path database = temp.resolve("morpheus.db");
        try (SqliteSpecificationKnowledgeStore ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Opening the store creates and validates the current schema.
        }
        SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
        SqliteServerMaintenance.BackupVerification backup =
                maintenance.createBackup(database, temp.resolve("backups"));
        Files.writeString(temp.resolve("morpheus.db-journal"), HOT_JOURNAL);

        SqliteServerMaintenance.BackupVerification restored =
                maintenance.restoreOffline(backup.path(), database, true);

        assertTrue(restored.integrityOk());
        assertEquals(backup.sha256(), restored.sha256());
        assertEquals(List.of(), strayEntries(temp),
                "a completed restore must discard the quarantine it took");
        assertFalse(Files.exists(temp.resolve("morpheus.db-journal")),
                "the previous database's journal must not survive a restore it does not belong to");
    }

    /**
     * The working files a restore creates and must never leave behind, whichever way it ended: the quarantined
     * copy of the previous database and the staging copy of the backup.
     */
    private static List<String> strayEntries(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.map(entry -> entry.getFileName().toString())
                    .filter(name -> name.contains(".pre-restore-") || name.startsWith(".morpheus-restore-"))
                    .sorted()
                    .toList();
        }
    }
}
