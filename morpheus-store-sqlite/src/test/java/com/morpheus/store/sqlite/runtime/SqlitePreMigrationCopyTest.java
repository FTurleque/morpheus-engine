package com.morpheus.store.sqlite.runtime;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Before the store advances the schema an existing database records, it keeps a copy of that database as it was;
 * when it cannot, it does not migrate (AUD-DEP-16).
 */
class SqlitePreMigrationCopyTest {
    private static final List<String> THROUGH_V012 = List.of(
            "V001__foundation", "V002__project_root_uniqueness", "V003__entity_identity_bindings",
            "V004__versioned_requirement_persistence", "V005__snapshot_traceability_persistence",
            "V006__snapshot_external_reference_persistence", "V007__snapshot_business_content_projection",
            "V008__sync_state_and_source_inventory", "V009__snapshot_acceptance_criteria", "V010__constraint_semantics",
            "V011__controlled_lifecycle_mutations", "V012__multi_provider_composition");
    private static final int ORIGINAL_VERSION = THROUGH_V012.size();
    private static final String COPY_MARKER = ".pre-migration-v012-";

    @TempDir
    Path tempDir;

    @Test
    void anOutdatedDatabaseIsCopiedAsItWasThenMigrated() throws Exception {
        Path database = outdatedDatabase("outdated.db");

        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Opening migrates; the copy must have been taken first.
        }

        List<Path> copies = copies(database);
        assertEquals(1, copies.size(), () -> "expected one pre-migration copy, found " + copies);
        try (Connection copy = connect(copies.get(0))) {
            assertEquals("ok", text(copy, "PRAGMA integrity_check"));
            assertEquals(ORIGINAL_VERSION, ledgerVersion(copy), "the copy records the version it was taken at");
            assertEquals(1, count(copy, "SELECT COUNT(*) FROM projects WHERE id = 'project-12'"));
            assertEquals(0, count(copy, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'saved_views'"),
                    "no object of a later migration may be in the copy");
        }
        try (Connection migrated = connect(database)) {
            assertEquals(SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION, ledgerVersion(migrated));
            assertEquals(1, count(migrated, "SELECT COUNT(*) FROM projects WHERE id = 'project-12'"));
        }
    }

    @Test
    void anOutdatedDatabaseOpenedInsideARequestScopeIsCopiedOnTheScopedConnection() throws Exception {
        Path database = outdatedDatabase("scoped.db");

        try (SqliteConnectionScope scope = SqliteConnectionScope.open(database, OperationalRecorder.noop());
             var first = new SqliteSpecificationKnowledgeStore(database);
             var second = new SqliteSpecificationKnowledgeStore(database)) {
            // Per-request runtimes open several stores on one shared connection: the first migrates, the others
            // find the schema ready and must not copy again.
        }

        List<Path> copies = copies(database);
        assertEquals(1, copies.size(), () -> "one copy for the scope, whatever the number of stores: " + copies);
        try (Connection copy = connect(copies.get(0))) {
            assertEquals(ORIGINAL_VERSION, ledgerVersion(copy));
        }
    }

    @Test
    void aNewDatabaseAndAnUpToDateDatabaseAreNotCopied() throws Exception {
        Path database = tempDir.resolve("fresh.db");

        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Creates and migrates a database that recorded nothing before.
        }
        List<String> afterCreation = listing();
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Already at the supported version: nothing to advance.
        }

        assertEquals(afterCreation, listing(), "reopening an up-to-date database must not add a file");
        assertTrue(afterCreation.stream().noneMatch(name -> name.contains(".pre-migration-")),
                () -> "a new database must not be copied: " + afterCreation);
    }

    @Test
    void aDatabaseAheadOfTheProgramIsRefusedWithoutACopy() throws Exception {
        Path database = tempDir.resolve("ahead.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Migrated to the supported version.
        }
        int ahead = SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION + 1;
        try (Connection connection = connect(database)) {
            execute(connection, "INSERT INTO schema_migrations(version, name, checksum, applied_at) VALUES ("
                    + ahead + ", 'future', 'future', '2026-10-11T00:00:00Z')");
        }

        KnowledgeStoreException refused = assertThrows(KnowledgeStoreException.class, () -> {
            try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
                // Must be refused before the store is usable.
            }
        });

        assertTrue(refused.getMessage().contains("is newer than supported"), refused::getMessage);
        assertTrue(copies(database).isEmpty(), "a database the program cannot migrate is not copied");
    }

    @Test
    void aDivergentHistoryIsStillRefusedAsAHistoryMismatch() throws Exception {
        Path database = outdatedDatabase("divergent.db");
        try (Connection connection = connect(database)) {
            execute(connection, "UPDATE schema_migrations SET checksum = 'not-the-checksum' WHERE version = 3");
        }

        KnowledgeStoreException refused = assertThrows(KnowledgeStoreException.class, () -> {
            try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
                // The migration refuses the recorded history.
            }
        });

        assertEquals("SQLite migration history mismatch for version 3", refused.getMessage());
        try (Connection connection = connect(database)) {
            assertEquals(ORIGINAL_VERSION, ledgerVersion(connection));
        }
    }

    @Test
    void aCopyThatCannotBeWrittenIsRefusedByNameWithoutAPathAndReplacesNothing() throws Exception {
        Path database = outdatedDatabase("unwritable.db");
        Path occupied = Path.of(database.toAbsolutePath() + COPY_MARKER + "occupied.db");
        Files.writeString(occupied, "not a database, and not ours to replace");
        SqlitePreMigrationCopy copy = new SqlitePreMigrationCopy(() -> "occupied");

        KnowledgeStoreException refused;
        try (Connection connection = connect(database)) {
            refused = assertThrows(KnowledgeStoreException.class,
                    () -> copy.keepIfTheSchemaWillAdvance(connection, database));
            assertEquals(ORIGINAL_VERSION, ledgerVersion(connection), "nothing may have been migrated");
        }

        assertTrue(refused.getMessage().startsWith(SqlitePreMigrationCopy.REFUSAL), refused::getMessage);
        assertTrue(refused.getMessage().contains("schema version " + ORIGINAL_VERSION), refused::getMessage);
        assertTrue(refused.getMessage().contains(
                "migrating it to " + SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION), refused::getMessage);
        assertFalse(refused.getMessage().contains(tempDir.getFileName().toString()),
                () -> "the refusal must not disclose a file-system location: " + refused.getMessage());
        assertEquals("not a database, and not ours to replace", Files.readString(occupied));
    }

    @Test
    void aRetryAfterAFailedMigrationWritesANewCopyAndLeavesTheFirstAlone() throws Exception {
        Path database = outdatedDatabase("retry.db");
        try (Connection connection = connect(database)) {
            // V014 creates this table, so the migration fails after the copy and rolls back in full.
            execute(connection, "CREATE TABLE saved_views (leftover TEXT)");
        }

        assertThrows(KnowledgeStoreException.class, () -> {
            try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
                // First attempt: copied, then refused by the migration.
            }
        });
        List<Path> afterFirstAttempt = copies(database);
        assertEquals(1, afterFirstAttempt.size());
        byte[] firstCopy = Files.readAllBytes(afterFirstAttempt.get(0));

        try (Connection connection = connect(database)) {
            assertEquals(ORIGINAL_VERSION, ledgerVersion(connection));
            execute(connection, "DROP TABLE saved_views");
        }
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Second attempt: a copy of its own, then the migration succeeds.
        }

        List<Path> afterSecondAttempt = copies(database);
        assertEquals(2, afterSecondAttempt.size(), () -> "each attempt keeps its own copy: " + afterSecondAttempt);
        assertArrayEquals(firstCopy, Files.readAllBytes(afterFirstAttempt.get(0)),
                "the copy of the first attempt must not be rewritten");
        try (Connection connection = connect(database)) {
            assertEquals(SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION, ledgerVersion(connection));
        }
    }

    @Test
    void aCopyTakenAfterSomeoneElseMigratedIsDiscarded() throws Exception {
        Path database = tempDir.resolve("raced.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Stands for the other process: the database is migrated between this process's read and its copy.
        }

        try (Connection connection = connect(database)) {
            new SqlitePreMigrationCopy().keepUnlessMigratedMeanwhile(connection, database, ORIGINAL_VERSION);
        }

        assertTrue(copies(database).isEmpty(),
                "a copy that records another version than the one it is named for is not a pre-migration copy");
    }

    @Test
    void twoConcurrentOpeningsLeaveOnlyTrueCopies() throws Exception {
        Path database = outdatedDatabase("concurrent.db");
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> opening = () -> {
            start.await();
            try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
                return "opened";
            } catch (KnowledgeStoreException refused) {
                // Two migrations of one database contend for it, and the loser may be refused as a storage failure.
                return String.valueOf(refused.getMessage());
            }
        };

        List<String> outcomes;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(opening);
            Future<String> second = executor.submit(opening);
            start.countDown();
            outcomes = List.of(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertTrue(outcomes.contains("opened"), () -> "one opening must complete the upgrade: " + outcomes);
        assertTrue(outcomes.stream().noneMatch(outcome -> outcome.contains("history mismatch")
                        || outcome.contains(SqlitePreMigrationCopy.REFUSAL)),
                () -> "contention must not read as a divergent history or a failed copy: " + outcomes);
        List<Path> copies = copies(database);
        assertFalse(copies.isEmpty(), "at least one opening found the outdated database and copied it");
        for (Path copy : copies) {
            try (Connection connection = connect(copy)) {
                assertEquals("ok", text(connection, "PRAGMA integrity_check"));
                assertEquals(ORIGINAL_VERSION, ledgerVersion(connection), () -> copy + " is not a pre-migration copy");
            }
        }
        try (Connection connection = connect(database)) {
            assertEquals(SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION, ledgerVersion(connection));
        }
    }

    /**
     * The trap the upgrade guide warns about: a backup taken by the new program is not a way back. Taking it opens the
     * database, and opening it migrates it, so the backup is already at the new schema. What still holds the database
     * as it was is the copy kept before the schema advanced.
     */
    @Test
    void aServerBackupOfAnOutdatedDatabaseIsAlreadyMigratedAndOnlyTheKeptCopyIsNot() throws Exception {
        Path database = outdatedDatabase("backed-up.db");
        Path backups = Files.createDirectory(tempDir.resolve("backups"));

        Path backup = new SqliteServerMaintenance().createBackup(database, backups).path();

        assertEquals(SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION, ledgerVersionOf(backup),
                "the backup of an outdated database is taken after its migration");
        List<Path> kept = copies(database);
        assertEquals(1, kept.size(), () -> "the migration behind the backup keeps its copy: " + kept);
        assertEquals(ORIGINAL_VERSION, ledgerVersionOf(kept.get(0)));
    }

    /**
     * A copy is never reused. A database restored offline to the old schema after a first upgrade is another database
     * at the same version: its copy must hold its content, not the content the first upgrade saved.
     */
    @Test
    void aDatabaseReplacedByAnotherOfTheSameOldVersionIsCopiedAsItIs() throws Exception {
        Path database = outdatedDatabase("replaced.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // First upgrade: one copy, holding project-12.
        }
        List<Path> afterTheFirstUpgrade = copies(database);
        assertEquals(1, afterTheFirstUpgrade.size());

        Path other = outdatedDatabase("other.db");
        try (Connection connection = connect(other)) {
            execute(connection, "UPDATE projects SET id = 'project-restored'");
        }
        Files.copy(other, database, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Second upgrade, of the restored database.
        }

        List<Path> afterTheSecondUpgrade = copies(database);
        assertEquals(2, afterTheSecondUpgrade.size(), () -> "the restored database keeps a copy of its own: "
                + afterTheSecondUpgrade);
        Path second = afterTheSecondUpgrade.stream()
                .filter(copy -> !copy.equals(afterTheFirstUpgrade.get(0))).findFirst().orElseThrow();
        assertEquals("project-12", projectIn(afterTheFirstUpgrade.get(0)));
        assertEquals("project-restored", projectIn(second));
        assertEquals(ORIGINAL_VERSION, ledgerVersionOf(second));
    }

    /** Measured, so that an operator knows what a database that cannot be migrated costs in disk: one copy per attempt. */
    @Test
    void aMigrationThatKeepsFailingKeepsOneCopyPerAttempt() throws Exception {
        Path database = outdatedDatabase("failing.db");
        try (Connection connection = connect(database)) {
            execute(connection, "CREATE TABLE saved_views (leftover TEXT)");
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThrows(KnowledgeStoreException.class, () -> {
                try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
                    // Copied, then refused by the migration.
                }
            });
            assertEquals(attempt, copies(database).size(), "one copy per attempt, none reused and none removed");
        }
        for (Path copy : copies(database)) {
            assertEquals(ORIGINAL_VERSION, ledgerVersionOf(copy));
        }
        assertEquals(ORIGINAL_VERSION, ledgerVersionOf(database), "a failed migration rolls back in full");
    }

    /**
     * Two MORPHEUS processes opening one outdated database at once, not two threads of one process: each has its own
     * JVM, its own connection and its own view of the file. Whichever migrates, every copy left behind must hold the
     * database as it was; a copy taken after the other process migrated would be a copy of the new schema under the
     * name of the old one. The process that loses the migration may be refused -- that predates the copy and is not
     * what this asserts.
     */
    @Test
    void twoProcessesOpeningOneOutdatedDatabaseLeaveOnlyTrueCopies() throws Exception {
        for (int round = 1; round <= 3; round++) {
            Path database = outdatedDatabase("two-processes-" + round + ".db");

            Process first = openInAnotherProcess(database);
            Process second = openInAnotherProcess(database);
            assertTrue(first.waitFor(120, TimeUnit.SECONDS) && second.waitFor(120, TimeUnit.SECONDS),
                    "a process opening the store did not end");

            List<Integer> exits = List.of(first.exitValue(), second.exitValue());
            assertTrue(exits.contains(0), () -> "one of the two processes must have opened the store: " + exits);
            assertTrue(exits.stream().allMatch(exit -> exit == 0 || exit == FixtureOpenStoreProcess.REFUSED),
                    () -> "a process ended otherwise than by opening the store or being refused by it: " + exits);
            assertEquals(SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION, ledgerVersionOf(database));
            List<Path> kept = copies(database);
            assertFalse(kept.isEmpty(), "the migration must have kept a copy");
            for (Path copy : kept) {
                assertEquals(ORIGINAL_VERSION, ledgerVersionOf(copy), () -> "a copy holds a migrated database: " + copy);
                assertEquals("project-12", projectIn(copy));
            }
        }
    }

    private static Process openInAnotherProcess(Path database) throws IOException {
        Path launcher = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        return new ProcessBuilder(launcher.toString(), "-cp", classpath,
                FixtureOpenStoreProcess.class.getName(), database.toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static int ledgerVersionOf(Path database) throws Exception {
        try (Connection connection = connect(database)) {
            return ledgerVersion(connection);
        }
    }

    private static String projectIn(Path database) throws Exception {
        try (Connection connection = connect(database)) {
            return text(connection, "SELECT id FROM projects");
        }
    }

    private Path outdatedDatabase(String name) throws Exception {
        Path database = tempDir.resolve(name);
        try (Connection connection = connect(database)) {
            execute(connection, """
                    CREATE TABLE schema_migrations (
                        version INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        checksum TEXT NOT NULL,
                        applied_at TEXT NOT NULL
                    )
                    """);
            for (int index = 0; index < THROUGH_V012.size(); index++) {
                String resource = THROUGH_V012.get(index);
                String script = load("/db/migration/" + resource + ".sql");
                for (String fragment : script.split(";")) {
                    if (!fragment.isBlank()) execute(connection, fragment);
                }
                try (var insert = connection.prepareStatement(
                        "INSERT INTO schema_migrations(version, name, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
                    insert.setInt(1, index + 1);
                    insert.setString(2, resource.substring(resource.indexOf("__") + 2).replace('_', '-'));
                    insert.setString(3, sha256(script));
                    insert.setString(4, "2026-10-11T00:00:00Z");
                    insert.executeUpdate();
                }
            }
            execute(connection, "INSERT INTO projects(id, root_scheme, root_value) VALUES ('project-12', 'file', 'ws')");
        }
        return database;
    }

    private List<Path> copies(Path database) throws IOException {
        String prefix = database.getFileName() + ".pre-migration-";
        try (var entries = Files.list(tempDir)) {
            return entries.filter(path -> path.getFileName().toString().startsWith(prefix)).sorted().toList();
        }
    }

    private List<String> listing() throws IOException {
        try (var entries = Files.list(tempDir)) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static int ledgerVersion(Connection connection) throws Exception {
        return count(connection, "SELECT COALESCE(MAX(version), 0) FROM schema_migrations");
    }

    private static int count(Connection connection, String query) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(query)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static String text(Connection connection, String query) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(query)) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Connection connect(Path database) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
    }

    private static String load(String resource) throws IOException {
        try (var stream = SqlitePreMigrationCopyTest.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("missing migration resource " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
