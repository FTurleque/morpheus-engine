package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Offline restore refuses to replace a database whose file or sidecars are not what they claim to be, and leaves the
 * live database untouched when it does; the backup checksum is the SHA-256 of the file (PIT-AUD-1: removing any of
 * these checks, or the digest update, failed no test).
 */
class SqliteOfflineRestoreEntryTest {
    @TempDir
    Path temp;

    @Test
    void aDatabaseThatIsASymbolicLinkIsRefused() throws Exception {
        assertSymbolicLinkRefused("", "database");
    }

    @Test
    void aJournalThatIsASymbolicLinkIsRefused() throws Exception {
        assertSymbolicLinkRefused("-journal", "SQLite journal");
    }

    @Test
    void aWalThatIsASymbolicLinkIsRefused() throws Exception {
        assertSymbolicLinkRefused("-wal", "SQLite WAL");
    }

    @Test
    void aShmThatIsASymbolicLinkIsRefused() throws Exception {
        assertSymbolicLinkRefused("-shm", "SQLite SHM");
    }

    @Test
    void aDatabaseThatIsADirectoryIsRefused() throws Exception {
        assertNonRegularRefused("", "database");
    }

    @Test
    void aJournalThatIsADirectoryIsRefused() throws Exception {
        assertNonRegularRefused("-journal", "SQLite journal");
    }

    @Test
    void aWalThatIsADirectoryIsRefused() throws Exception {
        assertNonRegularRefused("-wal", "SQLite WAL");
    }

    @Test
    void aShmThatIsADirectoryIsRefused() throws Exception {
        assertNonRegularRefused("-shm", "SQLite SHM");
    }

    private void assertSymbolicLinkRefused(String suffix, String label) throws Exception {
        Path database = temp.resolve("live.db");
        createDatabase(database);
        Path backup = new SqliteServerMaintenance().createBackup(database, temp.resolve("backups")).path();
        Path elsewhere = Files.writeString(temp.resolve("elsewhere"), "not a sidecar");
        Path liveFile = database;
        if (suffix.isEmpty()) {
            liveFile = Files.move(database, temp.resolve("real-live.db"));
        }
        Path entry = temp.resolve("live.db" + suffix);
        // The journal persists between connections (PERSIST mode): the entry is replaced, not created.
        Files.deleteIfExists(entry);
        assumeTrue(createSymlink(entry, suffix.isEmpty() ? liveFile : elsewhere),
                "symbolic links cannot be created in this environment");
        String liveBefore = sha256(liveFile);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new SqliteServerMaintenance().restoreOffline(backup, database, true));

        assertEquals(label + " must not be a symbolic link", refused.getMessage());
        assertEquals(liveBefore, sha256(liveFile), "a refused restore must not touch the live database");
    }

    private void assertNonRegularRefused(String suffix, String label) throws Exception {
        Path database = temp.resolve("live.db");
        createDatabase(database);
        Path backup = new SqliteServerMaintenance().createBackup(database, temp.resolve("backups")).path();
        Path liveFile = database;
        if (suffix.isEmpty()) {
            liveFile = Files.move(database, temp.resolve("kept-live.db"));
        }
        Files.deleteIfExists(temp.resolve("live.db" + suffix));
        Files.createDirectory(temp.resolve("live.db" + suffix));
        String liveBefore = sha256(liveFile);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new SqliteServerMaintenance().restoreOffline(backup, database, true));

        assertEquals(label + " must be a regular file", refused.getMessage());
        assertEquals(liveBefore, sha256(liveFile), "a refused restore must not touch the live database");
    }

    @Test
    void theBackupChecksumIsTheSha256OfTheBackupFile() throws Exception {
        Path database = temp.resolve("live.db");
        createDatabase(database);

        SqliteServerMaintenance.BackupVerification backup =
                new SqliteServerMaintenance().createBackup(database, temp.resolve("backups"));

        assertEquals(sha256(backup.path()), backup.sha256());
        assertEquals(Files.size(backup.path()), backup.bytes());
    }

    private static void createDatabase(Path database) {
        try (SqliteSpecificationKnowledgeStore ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Opening the store creates the current schema.
        }
    }

    /** Computed here, independently of the class under test. */
    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static boolean createSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            return false;
        }
    }
}
