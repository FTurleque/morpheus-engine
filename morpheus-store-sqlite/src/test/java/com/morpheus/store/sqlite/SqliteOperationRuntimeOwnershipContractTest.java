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
 * The runtime of one operation opens a connection, then the stores the operation asks for.
 *
 * <p>A store failing to open leaves the connection and every store opened before it holding a shared database lease:
 * they are released only if the runtime registered them as they opened. The exclusive server lease is refused while
 * any MORPHEUS connection is open, so taking it afterwards is what proves nothing was left holding the database.</p>
 */
class SqliteOperationRuntimeOwnershipContractTest {

    @TempDir
    Path temp;

    @Test
    void thePolicyServicesAreWiredAndTheDatabaseIsGivenBackOnClose() {
        Path database = temp.resolve("policy.db").toAbsolutePath().normalize();

        try (SqliteOperationRuntime runtime = SqliteOperationRuntime.open(database)) {
            assertNotNull(runtime.registry(), "the pack registry must be wired");
            assertNotNull(runtime.evaluation(), "the evaluation service must be wired");
        }

        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "closing the runtime must release every store it opened");
    }

    @Test
    void theQueryServicesAreWiredAndTheDatabaseIsGivenBackOnClose() {
        Path database = temp.resolve("query.db").toAbsolutePath().normalize();

        try (SqliteOperationRuntime runtime = SqliteOperationRuntime.open(database)) {
            assertNotNull(runtime.queries(), "the query execution service must be wired");
            assertNotNull(runtime.views(), "the saved view service must be wired");
            assertNotNull(runtime.exports(), "the export service must be wired");
        }

        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "closing the runtime must release every store it opened");
    }

    @Test
    void aPolicyStoreThatFailsToOpenLeavesNothingHoldingTheDatabase() throws Exception {
        Path database = temp.resolve("policy-future.db").toAbsolutePath().normalize();
        writeFutureSchema(database);

        try (SqliteOperationRuntime runtime = SqliteOperationRuntime.open(database)) {
            assertThrows(KnowledgeStoreException.class, runtime::registry);
        }

        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "a runtime whose store failed to open must release what it had already opened");
    }

    @Test
    void aQueryStoreThatFailsToOpenLeavesNothingHoldingTheDatabase() throws Exception {
        Path database = temp.resolve("query-future.db").toAbsolutePath().normalize();
        writeFutureSchema(database);

        try (SqliteOperationRuntime runtime = SqliteOperationRuntime.open(database)) {
            assertThrows(KnowledgeStoreException.class, runtime::views);
        }

        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "a runtime whose store failed to open must release what it had already opened");
    }

    /** Closing the runtime twice must not release a reference it no longer holds. */
    @Test
    void closingARuntimeTwiceReleasesItsStoresOnlyOnce() {
        Path database = temp.resolve("idempotent.db").toAbsolutePath().normalize();

        SqliteOperationRuntime runtime = SqliteOperationRuntime.open(database);
        runtime.registry();
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
