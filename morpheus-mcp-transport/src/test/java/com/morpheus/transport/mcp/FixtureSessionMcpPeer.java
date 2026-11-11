package com.morpheus.transport.mcp;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * Child-process peer for {@link McpPeerSessionTest}: one tool per answer the session must judge. With
 * {@code -Dfixture.echoOnly=true} it declares only the echo tool, so a session that requires more finds it
 * incompatible.
 */
public final class FixtureSessionMcpPeer {
    static final String TOOL_ECHO = "session_echo";
    static final String TOOL_ERROR = "session_error";
    static final String TOOL_EMPTY = "session_empty";
    static final String TOOL_PAIR = "session_pair";
    static final String TOOL_SLOW = "session_slow";

    private FixtureSessionMcpPeer() {
    }

    public static void main(String[] args) {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
                new JacksonMcpJsonMapperSupplier().get());
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("fixture-session-peer", "1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .build();
        server.addTool(tool(TOOL_ECHO, arguments -> answer(false,
                String.valueOf(arguments.getOrDefault("value", "")))));
        if (!Boolean.getBoolean("fixture.echoOnly")) {
            server.addTool(tool(TOOL_ERROR, arguments -> answer(true, "the peer refused")));
            server.addTool(tool(TOOL_EMPTY, arguments -> answer(false, "  ")));
            server.addTool(tool(TOOL_PAIR, arguments -> McpSchema.CallToolResult.builder(List.of(
                    McpSchema.TextContent.builder("one").build(),
                    McpSchema.TextContent.builder("two").build())).build()));
            server.addTool(tool(TOOL_SLOW, arguments -> {
                try {
                    Thread.sleep(120_000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return answer(false, "too late");
            }));
        }
        try {
            Thread.currentThread().join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            server.close();
        }
    }

    private static McpSchema.CallToolResult answer(boolean error, String text) {
        return McpSchema.CallToolResult.builder(List.of(McpSchema.TextContent.builder(text).build()))
                .isError(error)
                .build();
    }

    private static McpServerFeatures.SyncToolSpecification tool(
            String name,
            java.util.function.Function<Map<String, Object>, McpSchema.CallToolResult> handler) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, Map.of(
                "type", "object",
                "additionalProperties", true)).description("session fixture").build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handler.apply(
                        request.arguments() == null ? Map.of() : request.arguments()))
                .build();
    }
}
