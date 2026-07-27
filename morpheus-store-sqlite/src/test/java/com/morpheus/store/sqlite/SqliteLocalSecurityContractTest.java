package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteLocalSecurityContractTest {

    @TempDir
    Path tempDir;

    @Test
    void everyPublicSqliteAdapterUsesTheSecureDatabaseEntryPoint() throws Exception {
        List<Function<Path, AutoCloseable>> entryPoints = List.of(
                SqliteSpecificationKnowledgeStore::new,
                SqliteVersionedRequirementStore::new,
                SqliteSnapshotBusinessContentStore::new,
                SqliteTraceabilityStore::new,
                SqliteExternalReferenceStore::new,
                SqliteEntityIdentityStore::new,
                SqliteSyncStateStore::new,
                SqliteChangeLifecycleMutationStore::new,
                SqliteCompositionStateStore::new);

        for (int index = 0; index < entryPoints.size(); index++) {
            Path database = tempDir.resolve("entry-" + index).resolve("morpheus.db");
            try (AutoCloseable ignored = entryPoints.get(index).apply(database)) {
                assertTrue(Files.isRegularFile(database, LinkOption.NOFOLLOW_LINKS));
                assertFalse(Files.isSymbolicLink(database));
                assertOwnerOnlyWhenSupported(database);
            }
            assertSecurePersistentSidecars(database);
        }
    }

    @Test
    void sqliteSecureDefaultsUseOneOwnerOnlyPersistentJournalWithoutWalShm() throws Exception {
        Path database = tempDir.resolve("profile").resolve("morpheus.db");
        try (Connection connection = SqliteDatabaseSecurity.open(database);
             Statement statement = connection.createStatement()) {
            assertEquals("persist", pragmaText(statement, "journal_mode"));
            assertEquals("normal", pragmaText(statement, "locking_mode"));
            assertEquals(2, pragmaInt(statement, "temp_store"));
            assertEquals(1, pragmaInt(statement, "foreign_keys"));
            assertEquals(SqliteDatabaseSecurity.DEFAULT_BUSY_TIMEOUT_MILLIS,
                    pragmaInt(statement, "busy_timeout"));

            statement.execute("CREATE TABLE sidecar_probe(id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO sidecar_probe(value) VALUES ('local')");
            connection.commit();
        }

        assertOwnerOnlyWhenSupported(database);
        assertSecurePersistentSidecars(database);
    }

    @Test
    void activePersistentJournalRemainsOwnerOnlyInsideAPreexistingParent() throws Exception {
        Path parent = Files.createDirectories(tempDir.resolve("preexisting-parent"));
        Path database = parent.resolve("morpheus.db");
        Path journal = Path.of(database + "-journal");

        try (Connection connection = SqliteDatabaseSecurity.open(database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE journal_probe(id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO journal_probe(value) VALUES ('protected')");

            assertTrue(Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS));
            assertFalse(Files.exists(Path.of(database + "-wal"), LinkOption.NOFOLLOW_LINKS));
            assertFalse(Files.exists(Path.of(database + "-shm"), LinkOption.NOFOLLOW_LINKS));
            assertOwnerOnlyWhenSupported(journal);
            connection.rollback();
        }

        assertSecurePersistentSidecars(database);
    }

    private String pragmaText(Statement statement, String name) throws Exception {
        try (ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            assertTrue(result.next());
            return result.getString(1).toLowerCase(java.util.Locale.ROOT);
        }
    }

    private int pragmaInt(Statement statement, String name) throws Exception {
        try (ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private void assertSecurePersistentSidecars(Path database) throws Exception {
        assertFalse(Files.exists(Path.of(database + "-wal"), LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(Path.of(database + "-shm"), LinkOption.NOFOLLOW_LINKS));
        Path journal = Path.of(database + "-journal");
        assertTrue(Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS));
        assertOwnerOnlyWhenSupported(journal);
    }

    private void assertOwnerOnlyWhenSupported(Path file) throws Exception {
        PosixFileAttributeView posix = Files.getFileAttributeView(
                file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(
                file, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            var owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS);
            assertFalse(acl.getAcl().isEmpty());
            assertTrue(acl.getAcl().stream().allMatch(entry -> entry.principal().equals(owner)));
        }
    }
}
