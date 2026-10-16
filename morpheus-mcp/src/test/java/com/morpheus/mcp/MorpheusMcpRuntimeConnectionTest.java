package com.morpheus.mcp;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
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
 * connections. Counted here on the recorder the runtime was opened with, as absolute values.</p>
 */
class MorpheusMcpRuntimeConnectionTest {
    @TempDir
    Path temporaryDirectory;

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);

    @Test
    void oneRuntimeOpensOnePhysicalConnectionAndGivesItBack() {
        Path database = temporaryDirectory.resolve("morpheus.db");
        new SqliteSpecificationKnowledgeStore(database).close();

        try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(database, recorder)) {
            assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_OPENED),
                    "the stores of an MCP tool call share one physical connection");
            assertEquals(0, runtime.snapshots().listProjects().size());
        }

        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_OPENED));
        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_CLOSED));
        assertEquals(1L, counter(SqliteConnectionScope.SCHEMA_CHECKS));
    }

    private long counter(String name) {
        return metrics.snapshot().counters().getOrDefault(name, 0L);
    }

    @Test
    void aSecondRuntimeWhileTheFirstIsOpenIsRefusedByNameAndTheFirstStillWorks() {
        Path database = temporaryDirectory.resolve("morpheus.db");

        try (MorpheusMcpRuntime first = new MorpheusMcpRuntime(database, OperationalRecorder.noop())) {
            SqliteConnectionScope.NestedScopeException refused = assertThrows(
                    SqliteConnectionScope.NestedScopeException.class, () -> new MorpheusMcpRuntime(database, OperationalRecorder.noop()));

            assertTrue(refused.getMessage().startsWith(SqliteConnectionScope.NESTED_REFUSAL + ": "),
                    refused::getMessage);
            assertEquals(0, first.snapshots().listProjects().size());
        }
    }

    @Test
    void aDatabaseThatCannotBeOpenedIsTheStoreFailureEveryToolHandlerAnswers() throws Exception {
        Path database = temporaryDirectory.resolve("morpheus.db");
        Files.writeString(database, "not a SQLite database, long enough to fill a header page".repeat(16));

        KnowledgeStoreException refused =
                assertThrows(KnowledgeStoreException.class, () -> new MorpheusMcpRuntime(database, OperationalRecorder.noop()));

        assertTrue(refused.getMessage().startsWith("Cannot initialize SQLite "), refused::getMessage);
    }

    @Test
    void twoRuntimesBuiltOneAfterTheOtherOnOneThreadBothWork() {
        Path database = temporaryDirectory.resolve("morpheus.db");

        try (MorpheusMcpRuntime first = new MorpheusMcpRuntime(database, OperationalRecorder.noop())) {
            assertEquals(0, first.snapshots().listProjects().size());
        }
        try (MorpheusMcpRuntime second = new MorpheusMcpRuntime(database, OperationalRecorder.noop())) {
            assertEquals(0, second.snapshots().listProjects().size());
        }
    }
}
