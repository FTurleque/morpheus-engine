package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The runtime of one operation shares one physical connection and one schema check between every store it builds.
 *
 * <p>It replaced a query set of five stores and a policy set of eight, which the CLI, the MCP server and the HTTP
 * adapter read query and policy through, and the runtimes of seven to nine stores each transport kept. Counted here on
 * the scope's own diagnostics, which is what the runtime controls; the adapters count the connections of a whole
 * operation at the JDBC driver.</p>
 */
class SqliteOperationRuntimeConnectionTest {

    @TempDir
    Path temp;

    @Test
    void theQueryServicesShareOnePhysicalConnectionBetweenTheirFiveStores() {
        assertOneScopedConnection(runtime -> assertNotNull(runtime.views()), 5);
    }

    @Test
    void thePolicyServicesShareOnePhysicalConnectionBetweenTheirEightStores() {
        assertOneScopedConnection(runtime -> assertNotNull(runtime.registry()), 8);
    }

    @Test
    void everyStoreOfTheRuntimeSharesTheSameConnection() {
        assertOneScopedConnection(runtime -> {
            runtime.views();
            runtime.registry();
            runtime.identities();
            runtime.syncState();
            runtime.lifecycleMutations();
        }, 12);
    }

    @Test
    void aStoreIsBuiltOnceAndOnlyWhenAskedFor() {
        try (SqliteOperationRuntime runtime = SqliteOperationRuntime.open(temp.resolve("lazy.db"))) {
            assertEquals(0, runtime.logicalConnectionsBorrowed(), "opening the runtime builds no store");
            assertSame(runtime.portfolios(), runtime.portfolios());
            assertEquals(1, runtime.logicalConnectionsBorrowed(), "a store asked for twice is built once");
        }
    }

    @Test
    void aStoreAskedForAfterCloseIsRefusedRatherThanOpenedOnAConnectionOfItsOwn() {
        SqliteOperationRuntime runtime = SqliteOperationRuntime.open(temp.resolve("closed.db"));
        runtime.close();

        IllegalStateException refused = assertThrows(IllegalStateException.class, runtime::snapshots);

        assertEquals("the SQLite operation runtime is closed", refused.getMessage());
    }

    @Test
    void aRuntimeOpenedWhileAnotherIsOpenIsRefusedByNameAndTheFirstStillWorks() {
        Path database = temp.resolve("nested.db");

        try (SqliteOperationRuntime first = SqliteOperationRuntime.open(database)) {
            SqliteConnectionScope.NestedScopeException refused = assertThrows(
                    SqliteConnectionScope.NestedScopeException.class, () -> SqliteOperationRuntime.open(database));

            assertTrue(refused.getMessage().startsWith(SqliteConnectionScope.NESTED_REFUSAL + ": "),
                    refused::getMessage);
            assertNotNull(first.views());
        }
        try (SqliteOperationRuntime second = SqliteOperationRuntime.open(database)) {
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

        KnowledgeStoreException refused = assertThrows(
                KnowledgeStoreException.class, () -> SqliteOperationRuntime.open(database));

        assertEquals(SqliteSpecificationKnowledgeStore.INITIALIZATION_FAILURE, refused.getMessage());
        assertInstanceOf(SQLException.class, refused.getCause());
        assertEquals(before.active(), SqliteConnectionScope.diagnostics().active(), "and no connection is left open");
    }

    @Test
    void aDatabasePathThatIsADirectoryIsTheKnowledgeStoreFailureToo() throws Exception {
        Path directory = Files.createDirectory(temp.resolve("a-directory.db"));
        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();

        KnowledgeStoreException refused = assertThrows(
                KnowledgeStoreException.class, () -> SqliteOperationRuntime.open(directory));

        assertEquals(SqliteSpecificationKnowledgeStore.INITIALIZATION_FAILURE, refused.getMessage());
        assertInstanceOf(IllegalArgumentException.class, refused.getCause());
        assertEquals(before.active(), SqliteConnectionScope.diagnostics().active(), "and no connection is left open");
    }

    private void assertOneScopedConnection(Consumer<SqliteOperationRuntime> use, int stores) {
        Path database = temp.resolve("runtime.db");
        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();

        try (SqliteOperationRuntime runtime = SqliteOperationRuntime.open(database)) {
            use.accept(runtime);
            SqliteConnectionScope.Diagnostics during = SqliteConnectionScope.diagnostics();
            assertEquals(stores, runtime.logicalConnectionsBorrowed(), "the stores built for the operation");
            assertEquals(1, runtime.schemaInitializations(), "the " + stores + " stores check the schema once");
            assertEquals(1, during.opened() - before.opened(),
                    "the " + stores + " stores of the runtime share one physical connection");
            assertEquals(1, during.active() - before.active());
        }

        SqliteConnectionScope.Diagnostics after = SqliteConnectionScope.diagnostics();
        assertEquals(1, after.opened() - before.opened());
        assertEquals(1, after.closed() - before.closed());
        assertEquals(before.active(), after.active());
    }
}
