package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A database written by a build checked out with one line-ending convention must open with a build checked out with
 * the other: the release ships a Windows artifact built from a CRLF checkout and a Linux artifact built from an LF one,
 * from the same commit. Each test rewrites the ledger of a fresh database as the other build would have written it.
 */
class SqliteMigrationLineEndingCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void aLedgerWrittenByAnLfCheckoutOpensAndIsLeftAsRecorded() throws Exception {
        assertEquals(new TreeMap<>(SqliteMigrationChecksumGoldenTest.CANONICAL_CHECKSUMS), ledger("\n"));
        assertLedgerOpensUnchanged(tempDir.resolve("lf.db"), ledger("\n"));
    }

    @Test
    void aLedgerWrittenByACrlfCheckoutOpensAndIsLeftAsRecorded() throws Exception {
        assertLedgerOpensUnchanged(tempDir.resolve("crlf.db"), ledger("\r\n"));
    }

    @Test
    void aMigrationWhoseContentChangedIsStillRefused() throws Exception {
        Path database = tempDir.resolve("changed.db");
        Map<Integer, String> ledger = ledger("\n");
        ledger.put(1, sha256(canonicalScripts().get(1).replace("CREATE TABLE", "CREATE  TABLE")));
        rewriteLedger(database, ledger);

        KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class, () -> {
            try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
                // Opening must fail before the store becomes usable.
            }
        });
        assertEquals("SQLite migration history mismatch for version 1", refusal.getMessage());
    }

    private void assertLedgerOpensUnchanged(Path database, Map<Integer, String> ledger) throws Exception {
        rewriteLedger(database, ledger);

        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Reopening validates the recorded history against the runtime's migrations.
        }

        assertEquals(ledger, recordedLedger(database));
    }

    private void rewriteLedger(Path database, Map<Integer, String> ledger) throws Exception {
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Apply every migration first.
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var update = connection.prepareStatement("UPDATE schema_migrations SET checksum = ? WHERE version = ?")) {
            for (Map.Entry<Integer, String> entry : ledger.entrySet()) {
                update.setString(1, entry.getValue());
                update.setInt(2, entry.getKey());
                assertEquals(1, update.executeUpdate());
            }
        }
    }

    private static Map<Integer, String> recordedLedger(Path database) throws Exception {
        Map<Integer, String> recorded = new TreeMap<>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT version, checksum FROM schema_migrations")) {
            while (rows.next()) {
                recorded.put(rows.getInt("version"), rows.getString("checksum"));
            }
        }
        return recorded;
    }

    /** The ledger a build whose checkout used {@code lineEnding} writes for a fresh database. */
    static Map<Integer, String> ledger(String lineEnding) throws Exception {
        Map<Integer, String> ledger = new TreeMap<>();
        canonicalScripts().forEach((version, script) -> ledger.put(version, sha256(script.replace("\n", lineEnding))));
        return ledger;
    }

    /** Migration scripts with LF line endings, whatever the checkout wrote. */
    private static Map<Integer, String> canonicalScripts() throws Exception {
        Map<Integer, String> scripts = new TreeMap<>();
        try (Stream<Path> files = Files.list(migrationDirectory())) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                int version = Integer.parseInt(name.substring(1, name.indexOf("__")));
                scripts.put(version, Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"));
            }
        }
        assertEquals(SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION, scripts.size());
        return scripts;
    }

    private static Path migrationDirectory() throws URISyntaxException {
        return Path.of(SqliteMigrationLineEndingCompatibilityTest.class.getResource("/db/migration").toURI());
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
