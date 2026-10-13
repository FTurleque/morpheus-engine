package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SQLite-backed query and policy runtimes share one physical connection between their stores.
 *
 * <p>They are the store sets the CLI, the MCP server and the HTTP adapter read query and policy through. They opened
 * no connection scope: five physical connections and five schema checks for one query operation, eight for one policy
 * operation, on every transport that used them. Counted here on the scope's own diagnostics, which is what the set
 * controls; the adapters count the connections of a whole operation at the JDBC driver.</p>
 */
class SqliteRuntimeConnectionTest {

    @TempDir
    Path temp;

    @Test
    void theQueryRuntimeSharesOnePhysicalConnectionBetweenItsFiveStores() {
        assertOneScopedConnection(SqliteQueryRuntime::open, 5);
    }

    @Test
    void thePolicyRuntimeSharesOnePhysicalConnectionBetweenItsEightStores() {
        assertOneScopedConnection(SqlitePolicyRuntime::open, 8);
    }

    @Test
    void aRuntimeOpenedWhileAnotherIsOpenIsRefusedByNameAndTheFirstStillWorks() {
        Path database = temp.resolve("nested.db");

        try (SqliteQueryRuntime first = SqliteQueryRuntime.open(database)) {
            SqliteConnectionScope.NestedScopeException refused = assertThrows(
                    SqliteConnectionScope.NestedScopeException.class, () -> SqlitePolicyRuntime.open(database));

            assertTrue(refused.getMessage().startsWith(SqliteConnectionScope.NESTED_REFUSAL + ": "),
                    refused::getMessage);
            assertNotNull(first.views());
        }
        try (SqlitePolicyRuntime second = SqlitePolicyRuntime.open(database)) {
            assertEquals(0, second.registry().list().size(), "a runtime opened after the first closed works");
        }
    }

    /**
     * Opened store by store, a set reported a database it cannot open through its first store. Opened through a scope
     * the failure comes before any store exists, and it is still the one every adapter answers.
     */
    @Test
    void aDatabaseThatCannotBeOpenedIsStillTheKnowledgeStoreFailure() throws Exception {
        Path database = temp.resolve("not-a-database.db");
        Files.writeString(database, "not a SQLite database, long enough to fill a header page".repeat(16));
        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();

        for (Function<Path, AutoCloseable> open : List.<Function<Path, AutoCloseable>>of(
                SqliteQueryRuntime::open, SqlitePolicyRuntime::open)) {
            KnowledgeStoreException refused = assertThrows(KnowledgeStoreException.class, () -> open.apply(database));

            assertEquals(SqliteSpecificationKnowledgeStore.INITIALIZATION_FAILURE, refused.getMessage());
            assertInstanceOf(SQLException.class, refused.getCause());
        }
        assertEquals(before.active(), SqliteConnectionScope.diagnostics().active(), "and no connection is left open");
    }

    @Test
    void aDatabasePathThatIsADirectoryIsTheKnowledgeStoreFailureToo() throws Exception {
        Path directory = Files.createDirectory(temp.resolve("a-directory.db"));
        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();

        for (Function<Path, AutoCloseable> open : List.<Function<Path, AutoCloseable>>of(
                SqliteQueryRuntime::open, SqlitePolicyRuntime::open)) {
            KnowledgeStoreException refused = assertThrows(KnowledgeStoreException.class, () -> open.apply(directory));

            assertEquals(SqliteSpecificationKnowledgeStore.INITIALIZATION_FAILURE, refused.getMessage());
            assertInstanceOf(IllegalArgumentException.class, refused.getCause());
        }
        assertEquals(before.active(), SqliteConnectionScope.diagnostics().active(), "and no connection is left open");
    }

    private void assertOneScopedConnection(Function<Path, AutoCloseable> open, int stores) {
        Path database = temp.resolve("runtime.db");
        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();

        try (AutoCloseable runtime = open.apply(database)) {
            assertNotNull(runtime);
            SqliteConnectionScope.Diagnostics during = SqliteConnectionScope.diagnostics();
            assertEquals(1, during.opened() - before.opened(),
                    "the " + stores + " stores of the runtime share one physical connection");
            assertEquals(1, during.active() - before.active());
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }

        SqliteConnectionScope.Diagnostics after = SqliteConnectionScope.diagnostics();
        assertEquals(1, after.opened() - before.opened());
        assertEquals(1, after.closed() - before.closed());
        assertEquals(before.active(), after.active());
    }
}
