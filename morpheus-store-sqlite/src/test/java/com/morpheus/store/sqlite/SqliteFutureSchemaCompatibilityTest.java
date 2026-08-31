package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteFutureSchemaCompatibilityTest {
    @TempDir
    Path temp;

    @Test
    void refusesDatabaseCreatedByNewerMorpheusVersionBeforeApplyingKnownMigrations() throws Exception {
        Path database = temp.resolve("future.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE schema_migrations (
                        version INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        checksum TEXT NOT NULL,
                        applied_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO schema_migrations(version, name, checksum, applied_at)
                    VALUES (18, 'future', 'future-checksum', '2026-08-30T00:00:00Z')
                    """);
        }

        KnowledgeStoreException failure = assertThrows(
                KnowledgeStoreException.class,
                () -> new SqliteSpecificationKnowledgeStore(database));
        assertTrue(rootMessage(failure).contains("newer than supported 17"));

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'")) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1), "known migrations must not run against a future schema");
        }
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "" : current.getMessage();
    }
}
