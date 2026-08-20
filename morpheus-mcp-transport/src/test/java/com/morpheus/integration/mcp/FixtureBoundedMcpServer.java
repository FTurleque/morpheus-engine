package com.morpheus.integration.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/** Child-process fixture for exercising the bounded client transport over real MCP STDIO. */
public final class FixtureBoundedMcpServer {
    static final String TOOL_ECHO = "fixture_echo";
    static final String TOOL_LARGE = "fixture_large";

    private FixtureBoundedMcpServer() {
    }

    public static void main(String[] args) {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("fixture-bounded-transport", "1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .validateToolInputs(true)
                .build();
        server.addTool(tool(TOOL_ECHO, arguments -> String.valueOf(arguments.getOrDefault("value", ""))));
        server.addTool(tool(TOOL_LARGE, arguments -> {
            Object requested = arguments.getOrDefault("size", 4096);
            int size = requested instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(requested));
            return "x".repeat(Math.max(0, size));
        }));
        try {
            Thread.currentThread().join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            server.close();
        }
    }

    private static McpServerFeatures.SyncToolSpecification tool(
            String name,
            java.util.function.Function<Map<String, Object>, String> handler) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, Map.of(
                "type", "object",
                "additionalProperties", true)).description("bounded transport fixture").build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpSchema.CallToolResult.builder(List.of(
                        McpSchema.TextContent.builder(handler.apply(
                                request.arguments() == null ? Map.of() : request.arguments())).build()))
                        .build())
                .build();
    }
}
