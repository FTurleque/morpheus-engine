package com.morpheus.mcp;

import com.morpheus.store.sqlite.SqliteConnectionScope;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * One MCP tool call opens one physical SQLite connection, as the same operation does through HTTP and the CLI.
 *
 * <p>Counted at the JDBC driver on a tool call, not on a runtime: the query and policy tools read through store sets
 * that live in the SQLite adapter, which opened five and eight connections for one call while every rule looked at the
 * transport adapters. The two operations are the ones {@code HttpRequestConnectionCountTest} and
 * {@code CliCommandConnectionCountTest} measure on their transport. The database is initialised by a first call, so
 * that the count is the one of an ordinary call.</p>
 */
class McpToolCallConnectionCountTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void listingSavedViewsOpensOnePhysicalConnection() throws Exception {
        assertOneConnection(
                new MorpheusQueryMcpTools(database()).specifications(),
                MorpheusQueryMcpTools.LIST_SAVED_VIEWS,
                Map.of("scopeKind", "PROJECT", "scopeId", McpToolCall.ABSENT_PROJECT_ID));
    }

    @Test
    void listingPolicyPacksOpensOnePhysicalConnection() throws Exception {
        assertOneConnection(
                new MorpheusPolicyMcpTools(database()).specifications(), MorpheusPolicyMcpTools.LIST, Map.of());
    }

    private Path database() {
        return temporaryDirectory.resolve("morpheus.db");
    }

    private static void assertOneConnection(
            List<McpServerFeatures.SyncToolSpecification> tools, String tool, Map<String, Object> arguments)
            throws Exception {
        answered(McpToolCall.call(tools, tool, arguments));
        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();

        int opened = PhysicalSqliteConnections.openedDuring(() -> answered(McpToolCall.call(tools, tool, arguments)));

        assertEquals(1, opened, tool + " opens one physical SQLite connection");
        assertEquals(1, SqliteConnectionScope.diagnostics().opened() - before.opened(),
                "and that connection is the one of a scope, whose schema check runs once");
    }

    private static void answered(McpSchema.CallToolResult result) {
        assertNotEquals(Boolean.TRUE, result.isError(), () -> String.valueOf(result.content()));
    }
}
