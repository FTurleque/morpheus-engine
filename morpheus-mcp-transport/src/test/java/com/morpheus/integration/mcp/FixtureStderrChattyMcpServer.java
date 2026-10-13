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
 * Child-process fixture that writes diagnostic lines to stderr before serving MCP requests over stdio, used to
 * exercise the client transport's stderr handling without disturbing the stdout protocol stream.
 *
 * <p>The first line is not UTF-8: a peer logs in the code page of its platform, and a French Windows writes an
 * accented letter as one byte that no UTF-8 sequence starts with.</p>
 */
public final class FixtureStderrChattyMcpServer {
    static final String TOOL_ECHO = "fixture_stderr_echo";
    static final String PLAIN_DIAGNOSTIC = "fixture-stderr-chatty: diagnostic line before serving";
    /** {@code peut etre accessible} with its circumflex written as the single Windows-1252 byte 0xEA. */
    static final byte[] DIAGNOSTIC_IN_A_LEGACY_CODE_PAGE = {
            'p', 'e', 'u', 't', ' ', (byte) 0xEA, 't', 'r', 'e', ' ', 'a', 'c', 'c', 'e', 's', 's', 'i', 'b', 'l', 'e', '\n'};

    private FixtureStderrChattyMcpServer() {
    }

    public static void main(String[] args) {
        System.err.write(DIAGNOSTIC_IN_A_LEGACY_CODE_PAGE, 0, DIAGNOSTIC_IN_A_LEGACY_CODE_PAGE.length);
        System.err.flush();
        System.err.println(PLAIN_DIAGNOSTIC);
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
