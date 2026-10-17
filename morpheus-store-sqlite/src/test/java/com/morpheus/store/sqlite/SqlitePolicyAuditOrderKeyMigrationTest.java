package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Migration 21 gives every audit record that existed before it the order key the store now writes, so a journal
 * written by an earlier version is read in the same order as one written by this one (AUD-PRF-04).
 */
class SqlitePolicyAuditOrderKeyMigrationTest {
    /** Texts as {@code Instant.toString()} writes them: no fraction, three, six and nine digits. */
    private static final List<String> INSTANTS = List.of(
            "2026-10-09T14:30:00Z",
            "2026-10-09T14:30:00.500Z",
            "2026-10-09T14:30:00.500100Z",
            "2026-10-09T14:30:00.000000001Z",
            "2026-10-09T14:30:00.999999999Z",
            "2026-10-09T14:30:01Z",
            "1970-01-01T00:00:00Z",
            "2099-12-31T23:59:59.123456Z");

    @TempDir
    Path tempDir;

    @Test
    void theKeyOfARecordThatExistedBeforeTheMigrationIsTheKeyThisVersionWouldHaveWritten() throws Exception {
        Path database = tempDir.resolve("v20-upgrade.db");
        try (var ignored = new SqlitePolicyPackStore(database)) {
            // build the current schema, then step back over V021 only
        }
        String url = "jdbc:sqlite:" + database.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP INDEX idx_policy_audit_pack_order");
            statement.execute("ALTER TABLE policy_audit DROP COLUMN at_key");
            statement.execute("DELETE FROM schema_migrations WHERE version = 21");
            statement.execute(
                    "INSERT INTO policy_packs(id, name, revision, latest_version_number, created_at, updated_at) "
                            + "VALUES ('pack', 'p', 1, 1, '2026-10-09T14:30:00Z', '2026-10-09T14:30:00Z')");
        }
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO policy_audit(id, action, pack_id, actor, reason, at) "
                             + "VALUES (?, 'CREATE', 'pack', 'alice', 'reason', ?)")) {
            for (int index = 0; index < INSTANTS.size(); index++) {
                insert.setString(1, "audit-" + index);
                insert.setString(2, INSTANTS.get(index));
                insert.executeUpdate();
            }
        }

        try (var ignored = new SqlitePolicyPackStore(database)) {
            // reopening applies V021
        }

        Map<String, String> keys = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT at, at_key FROM policy_audit ORDER BY id")) {
            while (rows.next()) {
                keys.put(rows.getString("at"), rows.getString("at_key"));
            }
        }
        assertEquals(INSTANTS.size(), keys.size());
        for (String instant : INSTANTS) {
            assertNotNull(keys.get(instant), instant);
            assertEquals(SqlitePolicyPackStore.orderKey(Instant.parse(instant)), keys.get(instant), instant);
        }
    }

    @Test
    void theKeysSortAsTheInstantsDo() {
        List<Instant> chronological = INSTANTS.stream().map(Instant::parse).sorted().toList();
        List<String> keyed = chronological.stream().map(SqlitePolicyPackStore::orderKey).toList();

        assertEquals(keyed.stream().sorted().toList(), keyed, "text order of the keys is the order of the instants");
        assertEquals(keyed.size(), keyed.stream().distinct().count());
    }
}
