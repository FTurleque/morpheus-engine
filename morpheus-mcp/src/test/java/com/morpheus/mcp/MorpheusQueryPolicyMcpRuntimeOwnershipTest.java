package com.morpheus.mcp;

import com.morpheus.store.sqlite.SqliteServerMaintenance;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The query and policy MCP runtimes open five and seven SQLite stores while assembling themselves.
 *
 * <p>They opened them straight into their fields, so a store failing partway left every one opened before it
 * holding a connection and a shared database lease with nothing able to release them: the constructor never
 * returns, so the runtime that would have closed them was never built. They also released those stores as a
 * bare sequence of close calls, where the first failure leaves the rest held.</p>
 *
 * <p>Calling a tool builds the runtime and closes it, which is what exercises both paths. The exclusive server
 * lease is refused while any MORPHEUS connection is open, so acquiring it afterwards is what proves nothing was
 * left holding the database — on the successful path and on the failed one alike.</p>
 */
class MorpheusQueryPolicyMcpRuntimeOwnershipTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void theQueryRuntimeReleasesEveryStoreItOpened() {
        Path database = temporaryDirectory.resolve("query.db").toAbsolutePath().normalize();

        McpSchema.CallToolResult result = call(
                new MorpheusQueryMcpTools(database).specifications(),
                MorpheusQueryMcpTools.LIST_SAVED_VIEWS,
                Map.of());

        assertNotNull(result);
        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "the query runtime must have released every store it opened");
    }

    @Test
    void thePolicyRuntimeReleasesEveryStoreItOpened() {
        Path database = temporaryDirectory.resolve("policy.db").toAbsolutePath().normalize();

        McpSchema.CallToolResult result = call(
                new MorpheusPolicyMcpTools(database).specifications(),
                MorpheusPolicyMcpTools.LIST,
                Map.of());

        assertNotNull(result);
        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "the policy runtime must have released every store it opened");
    }

    @Test
    void aStoreThatFailsPartwayLeavesNothingHoldingTheDatabase() throws Exception {
        Path database = temporaryDirectory.resolve("future-schema.db").toAbsolutePath().normalize();
        writeFutureSchema(database);

        List<McpServerFeatures.SyncToolSpecification> query =
                new MorpheusQueryMcpTools(database).specifications();
        List<McpServerFeatures.SyncToolSpecification> policy =
                new MorpheusPolicyMcpTools(database).specifications();

        // The tool answers with an error result rather than propagating, so the assertion that matters is the
        // one below: whatever the runtime managed to open before the refusal must not still be holding it.
        assertNotNull(call(query, MorpheusQueryMcpTools.LIST_SAVED_VIEWS, Map.of()));
        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "a partially assembled query runtime must release what it had already opened");

        assertNotNull(call(policy, MorpheusPolicyMcpTools.LIST, Map.of()));
        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "a partially assembled policy runtime must release what it had already opened");
    }

    /** A database a newer MORPHEUS wrote: refused fail-closed once the first store tries to migrate it. */
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

    private static McpSchema.CallToolResult call(
            List<McpServerFeatures.SyncToolSpecification> specifications,
            String toolName,
            Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification specification = specifications.stream()
                .filter(item -> item.tool().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool not published: " + toolName));
        return specification.callHandler().apply(
                null, new McpSchema.CallToolRequest(toolName, arguments));
    }

    /** A tool that does not exist must not silently pass as one that does. */
    @Test
    void theHelperFailsWhenTheToolIsNotPublished() {
        List<McpServerFeatures.SyncToolSpecification> specifications =
                new MorpheusQueryMcpTools(temporaryDirectory.resolve("absent.db")).specifications();

        assertThrows(AssertionError.class, () -> call(specifications, "no_such_tool", Map.of()));
    }
}
