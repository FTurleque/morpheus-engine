package com.morpheus.mcp;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.store.sqlite.runtime.SqliteConnectionScope;
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
 * <p>Counted on a tool call: the query and policy tools read through store sets
 * that live in the SQLite adapter, which opened five and eight connections for one call while every rule looked at the
 * transport adapters. The two operations are the ones {@code HttpRequestConnectionCountTest} and
 * {@code CliCommandConnectionCountTest} measure on their transport. The database is initialised by a first call, so
 * that the count is the one of an ordinary call.
 * The count is the scope's own, read on the recorder this test hands in, so it shares nothing with another
 * test of the JVM. A store built outside a scope opens a connection that no scope counts; that is refused
 * where it is written, by {@code SqliteConnectionScopeUseArchitectureTest}, and no longer observed at the JDBC
 * driver, which meant swapping the driver of the whole JVM (AUD-QUA-17).</p>
 */
class McpToolCallConnectionCountTest {
    @TempDir
    Path temporaryDirectory;

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);

    @Test
    void listingSavedViewsOpensOnePhysicalConnection() throws Exception {
        assertOneConnection(
                new MorpheusQueryMcpTools(database(), recorder).specifications(),
                MorpheusQueryMcpTools.LIST_SAVED_VIEWS,
                Map.of("scopeKind", "PROJECT", "scopeId", McpToolCall.ABSENT_PROJECT_ID));
    }

    @Test
    void listingPolicyPacksOpensOnePhysicalConnection() throws Exception {
        assertOneConnection(
                new MorpheusPolicyMcpTools(database(), recorder).specifications(), MorpheusPolicyMcpTools.LIST, Map.of());
    }

    private Path database() {
        return temporaryDirectory.resolve("morpheus.db");
    }

    private void assertOneConnection(
            List<McpServerFeatures.SyncToolSpecification> tools, String tool, Map<String, Object> arguments)
            throws Exception {
        answered(McpToolCall.call(tools, tool, arguments));
        Map<String, Long> before = metrics.snapshot().counters();

        answered(McpToolCall.call(tools, tool, arguments));

        Map<String, Long> after = metrics.snapshot().counters();
        assertEquals(1L, delta(before, after, SqliteConnectionScope.CONNECTIONS_OPENED),
                tool + " opens one physical SQLite connection, the one of a scope, counted on the recorder the "
                        + "tools were handed");
        assertEquals(1L, delta(before, after, SqliteConnectionScope.SCHEMA_CHECKS), "whose schema check runs once");
    }

    private static long delta(Map<String, Long> before, Map<String, Long> after, String counter) {
        return after.getOrDefault(counter, 0L) - before.getOrDefault(counter, 0L);
    }

    private static void answered(McpSchema.CallToolResult result) {
        assertNotEquals(Boolean.TRUE, result.isError(), () -> String.valueOf(result.content()));
    }
}
