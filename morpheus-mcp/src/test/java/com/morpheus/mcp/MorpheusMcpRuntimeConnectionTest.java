package com.morpheus.mcp;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.store.sqlite.SqliteConnectionScope;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An MCP tool call opens one SQLite connection, as the same operation does through HTTP or the CLI.
 *
 * <p>The MCP runtime opened its seven stores without a connection scope: seven physical connections and seven schema
 * checks for one tool call, where the HTTP runtime shares one connection between nine stores. Two transports the
 * convergence manifest declares equivalent put different pressure on the database, and read it through different
 * connections. Counted here on the process-wide diagnostics of the scope, read as a difference around one runtime.</p>
 */
class MorpheusMcpRuntimeConnectionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void oneRuntimeOpensOnePhysicalConnectionAndGivesItBack() {
        Path database = temporaryDirectory.resolve("morpheus.db");
        new SqliteSpecificationKnowledgeStore(database).close();
        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();

        try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(database)) {
            assertEquals(1, SqliteConnectionScope.diagnostics().opened() - before.opened(),
                    "the seven stores of an MCP tool call share one physical connection");
            assertEquals(0, runtime.snapshots.listProjects().size());
        }

        SqliteConnectionScope.Diagnostics after = SqliteConnectionScope.diagnostics();
        assertEquals(1, after.opened() - before.opened());
        assertEquals(1, after.closed() - before.closed());
    }

    @Test
    void aSecondRuntimeWhileTheFirstIsOpenIsRefusedByNameAndTheFirstStillWorks() {
        Path database = temporaryDirectory.resolve("morpheus.db");

        try (MorpheusMcpRuntime first = new MorpheusMcpRuntime(database)) {
            IllegalStateException refused =
                    assertThrows(IllegalStateException.class, () -> new MorpheusMcpRuntime(database));

            assertTrue(refused.getMessage().startsWith(SqliteConnectionScope.NESTED_REFUSAL + ": "),
                    refused::getMessage);
            assertEquals(0, first.snapshots.listProjects().size());
        }
    }

    @Test
    void aDatabaseThatCannotBeOpenedIsTheStoreFailureEveryToolHandlerAnswers() throws Exception {
        Path database = temporaryDirectory.resolve("morpheus.db");
        Files.writeString(database, "not a SQLite database, long enough to fill a header page".repeat(16));

        KnowledgeStoreException refused =
                assertThrows(KnowledgeStoreException.class, () -> new MorpheusMcpRuntime(database));

        assertTrue(refused.getMessage().startsWith("Cannot initialize SQLite "), refused::getMessage);
    }

    @Test
    void twoRuntimesBuiltOneAfterTheOtherOnOneThreadBothWork() {
        Path database = temporaryDirectory.resolve("morpheus.db");

        try (MorpheusMcpRuntime first = new MorpheusMcpRuntime(database)) {
            assertEquals(0, first.snapshots.listProjects().size());
        }
        try (MorpheusMcpRuntime second = new MorpheusMcpRuntime(database)) {
            assertEquals(0, second.snapshots.listProjects().size());
        }
    }
}
