package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The checksum a database records for each migration is pinned here as a literal, not recomputed from the resources:
 * a test that derives its expectation from the file it checks cannot notice that the file changed, nor that the
 * checkout changed its line endings. The values are the SHA-256 of each script with LF line endings, which is what
 * git stores; a Windows checkout and a Linux checkout of the same commit must record the same values.
 */
class SqliteMigrationChecksumGoldenTest {

    static final Map<Integer, String> CANONICAL_CHECKSUMS = Map.ofEntries(
            entry(1, "eeb86cf37c20771bfb99c7686a93463131de8c8d0628510e9a09d1d01735e84b"),
            entry(2, "9dbdb7268e3dd3c191d71bcc2d79d867b314a2a55fa9951ee4a3a7b46bc4ac66"),
            entry(3, "418817976871b26ff3673e8cc157f7725364055468a47dde5b689b69650c3c7c"),
            entry(4, "2f1ac966b2700d6ed21a9713c645e273f9cd64450185a73c219c1fdac6decd5f"),
            entry(5, "9b934311c64df8199d830399a72b4117d3802106e423df04d561587fe19d1160"),
            entry(6, "76e1e8aba319a719db20193fd13165596f239931b42e08dc739185f921e459e5"),
            entry(7, "719d2d651dbcfc463369a56ebbbde8c5e6d1e4d88d286743a4121f1cd0ca4480"),
            entry(8, "61d22b17eb7ecbd042838aa4512f482b1c6fe07465663e3f2521ff7587a1f9ae"),
            entry(9, "cc3ee4f7cd1346c751fac63b248e8b7113925c7b829154a5fb9d58d67f951bf3"),
            entry(10, "77e816c17f3b9fa5f37eefed7f592593eeb7828a2bb4d8e8f27ff67fa56862d9"),
            entry(11, "f1af2524bebbf527881774c6870387e4eddc40c65bb26a96dc54ce293dce29f4"),
            entry(12, "e020cc6adbb4e07dcc75663ea6d9f80d77689c8950447bb8995d3f7d58fdcc43"),
            entry(13, "907f6d7807bdd6b449e881ff47415b17dff4f4172a1c3a67f7d200e1ca788818"),
            entry(14, "fa678a313dac7c94f7f513a101fe6328b15e805e87682a51f1a47e5d8aa459cf"),
            entry(15, "3b7f41438a3d675d32e61c55a43d73135a51377e833944ba20630e2c2a9ff3cc"),
            entry(16, "5ed2bfbf206cc545be07c7310ae6f0b6a1fe7b65bec9cccbf527bb34a170a6bb"),
            entry(17, "343fad6b1f1e045aff8a3e476affd882f6ad65dc55fc417f0093d62be3f41c40"),
            entry(18, "ac4cd2cd13c7d81093ad10a65f0d0ff430723576a0086185263747f661c48aac"),
            entry(19, "cb176cd968bdd5744be524ca5a92e7745afaf37087d729e5895711c547f7e933"),
            entry(20, "1c84d7951c59d07aaefdc7f6a753b5f31d5f3c8278ee3078027d69bbc753a3ae"));

    @TempDir
    Path tempDir;

    @Test
    void everyMigrationIsPinned() {
        assertEquals(SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION, CANONICAL_CHECKSUMS.size(),
                "a new migration needs its canonical checksum pinned here");
    }

    @Test
    void aNewDatabaseRecordsTheCanonicalChecksumOfEveryMigration() throws Exception {
        Path database = tempDir.resolve("golden.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Constructor applies migrations.
        }

        Map<Integer, String> recorded = new TreeMap<>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT version, checksum FROM schema_migrations ORDER BY version")) {
            while (rows.next()) {
                recorded.put(rows.getInt("version"), rows.getString("checksum"));
            }
        }
        assertEquals(new TreeMap<>(CANONICAL_CHECKSUMS), recorded);
    }
}
