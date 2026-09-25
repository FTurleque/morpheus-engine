package com.morpheus.store.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The renamed resolution value is migrated in stored rows, or every existing composition would stop being readable. */
class SqliteCompositionResolutionMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void migrationTwentyRenamesTheStoredPrecedenceResolution() throws Exception {
        Path database = tempDir.resolve("composition-v19-upgrade.db");
        try (var ignored = new SqliteCompositionStateStore(database)) {
            // build the current schema, then step back over V020 only
        }
        String url = "jdbc:sqlite:" + database.toAbsolutePath();
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.execute("DELETE FROM schema_migrations WHERE version = 20");
            statement.execute("INSERT INTO composition_snapshot_state(snapshot_id, primary_provider_id) VALUES ('s', 'p')");
        }
        try (var connection = DriverManager.getConnection(url);
             var insert = connection.prepareStatement("INSERT INTO composition_conflict(snapshot_id, entity_type, "
                     + "logical_key, field_name, resolution, selected_provider_id, reason) "
                     + "VALUES ('s', 'REQUIREMENT', 'k', 'title', ?, 'p', 'r')")) {
            insert.setString(1, "SELECTED_BY_PRECEDENCE");
            insert.executeUpdate();
        }

        try (var ignored = new SqliteCompositionStateStore(database)) {
            // reopening applies V020
        }

        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT resolution FROM composition_conflict")) {
            result.next();
            assertEquals("PRECEDENCE_RECORDED", result.getString(1));
        }
    }
}
