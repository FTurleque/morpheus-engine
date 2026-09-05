package com.morpheus.api;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.store.sqlite.SqliteServerMaintenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A specialised runtime acquires a connection scope and then several stores, and the assembly can fail at any
 * of them.
 *
 * <p>The scope is acquired first, so a store that fails afterwards leaves it behind unless something owns it:
 * the constructor never returns, so the object that would have closed it was never built. A database written
 * by a newer MORPHEUS is refused fail-closed after the scope exists, which is exactly that shape.</p>
 *
 * <p>The exclusive server lease is refused while any MORPHEUS connection is open, so acquiring it afterwards is
 * what proves nothing was left holding the database.</p>
 */
class MorpheusQueryRuntimeOwnershipTest {

    @TempDir
    Path tempDirectory;

    @Test
    void aStoreThatFailsAfterTheScopeIsOpenLeavesNothingHoldingTheDatabase() throws Exception {
        Path database = tempDirectory.resolve("future-schema.db").toAbsolutePath().normalize();
        writeFutureSchema(database);

        MorpheusQueryApiService service = new MorpheusQueryApiService(database);

        assertThrows(KnowledgeStoreException.class,
                () -> service.executeProject(
                        "01890f7a-36d4-7c1e-8000-000000000071",
                        new MorpheusQueryApiService.QueryRequest("change", null, null, null, null, null)));

        assertDoesNotThrow(
                () -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "the partially assembled runtime must have released its connection scope and stores");
    }

    private static void writeFutureSchema(Path database) throws Exception {
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
                    VALUES (9999, 'written-by-a-newer-morpheus', 'checksum', '2026-09-03T00:00:00Z')
                    """);
        }
    }
}
