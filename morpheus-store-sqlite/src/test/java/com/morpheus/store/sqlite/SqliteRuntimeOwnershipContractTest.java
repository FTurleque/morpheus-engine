package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The SQLite-backed policy and query runtimes open several stores before they are finished being built.
 *
 * <p>A store failing partway leaves every one opened before it holding a connection and a shared database
 * lease with nothing able to release them: the factory never returns, so the runtime that would have closed
 * them is never built. The exclusive server lease is refused while any MORPHEUS connection is open, so taking
 * it afterwards is what proves nothing was left holding the database.</p>
 */
class SqliteRuntimeOwnershipContractTest {

    @TempDir
    Path temp;

    @Test
    void thePolicyRuntimeOpensItsServicesAndGivesTheDatabaseBackOnClose() {
        Path database = temp.resolve("policy.db").toAbsolutePath().normalize();

        try (SqlitePolicyRuntime runtime = SqlitePolicyRuntime.open(database)) {
            assertNotNull(runtime.registry(), "the pack registry must be wired");
            assertNotNull(runtime.evaluation(), "the evaluation service must be wired");
        }

        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "closing the policy runtime must release every store it opened");
    }

    @Test
    void theQueryRuntimeOpensItsServicesAndGivesTheDatabaseBackOnClose() {
        Path database = temp.resolve("query.db").toAbsolutePath().normalize();

        try (SqliteQueryRuntime runtime = SqliteQueryRuntime.open(database)) {
            assertNotNull(runtime.queries(), "the query execution service must be wired");
            assertNotNull(runtime.views(), "the saved view service must be wired");
            assertNotNull(runtime.exports(), "the export service must be wired");
        }

        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "closing the query runtime must release every store it opened");
    }

    @Test
    void aPolicyStoreThatFailsPartwayReleasesTheStoresOpenedBeforeIt() throws Exception {
        Path database = temp.resolve("policy-future.db").toAbsolutePath().normalize();
        writeFutureSchema(database);

        assertThrows(KnowledgeStoreException.class, () -> SqlitePolicyRuntime.open(database));

        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "a partially assembled policy runtime must release what it had already opened");
    }

    @Test
    void aQueryStoreThatFailsPartwayReleasesTheStoresOpenedBeforeIt() throws Exception {
        Path database = temp.resolve("query-future.db").toAbsolutePath().normalize();
        writeFutureSchema(database);

        assertThrows(KnowledgeStoreException.class, () -> SqliteQueryRuntime.open(database));

        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "a partially assembled query runtime must release what it had already opened");
    }

    /** Closing the store set twice must not release a reference the runtime no longer holds. */
    @Test
    void closingARuntimeTwiceReleasesItsStoresOnlyOnce() {
        Path database = temp.resolve("idempotent.db").toAbsolutePath().normalize();

        SqlitePolicyRuntime runtime = SqlitePolicyRuntime.open(database);
        runtime.close();
        assertDoesNotThrow(runtime::close, "a second close must be a no-op, not a second release");

        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close());
    }

    /** A database a newer MORPHEUS wrote: refused fail-closed once a store tries to migrate it. */
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
