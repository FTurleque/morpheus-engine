package com.morpheus.integration.mcp;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * Child-process fixture that writes a diagnostic line to stderr before serving MCP requests over stdio, used to
 * exercise the client transport's stderr handling without disturbing the stdout protocol stream.
 */
public final class FixtureStderrChattyMcpServer {
    static final String TOOL_ECHO = "fixture_stderr_echo";

    private FixtureStderrChattyMcpServer() {
    }

    public static void main(String[] args) {
        System.err.println("fixture-stderr-chatty: diagnostic line before serving");
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
                new JacksonMcpJsonMapperSupplier().get());
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("fixture-stderr-chatty-transport", "1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .validateToolInputs(true)
                .build();
        server.addTool(tool(TOOL_ECHO, arguments -> String.valueOf(arguments.getOrDefault("value", ""))));
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
                "additionalProperties", true)).description("stderr-chatty transport fixture").build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpSchema.CallToolResult.builder(List.of(
                        McpSchema.TextContent.builder(handler.apply(
                                request.arguments() == null ? Map.of() : request.arguments())).build()))
                        .build())
                .build();
    }
}
