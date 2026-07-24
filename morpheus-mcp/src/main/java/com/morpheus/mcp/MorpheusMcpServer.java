package com.morpheus.mcp;

import com.morpheus.application.store.KnowledgeStoreException;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Native STDIO MCP adapter. Stdout is owned exclusively by the MCP transport. */
public final class MorpheusMcpServer {
    public static final String SERVER_NAME = "morpheus";
    public static final String SERVER_VERSION = "0.1.0-SNAPSHOT";

    private MorpheusMcpServer() {
    }

    public static McpSyncServer build(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        MorpheusMcpToolCatalog catalog = new MorpheusMcpToolCatalog();
        MorpheusMcpToolService service = new MorpheusMcpToolService(databasePath);
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .validateToolInputs(true)
                .build();

        for (MorpheusMcpToolCatalog.ToolDefinition definition : catalog.tools()) {
            server.addTool(tool(definition, service));
        }
        return server;
    }

    public static int run(Path databasePath) {
        McpSyncServer server = build(databasePath);
        try {
            Thread.currentThread().join();
            return 0;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return 0;
        } finally {
            server.close();
        }
    }

    static McpServerFeatures.SyncToolSpecification tool(
            MorpheusMcpToolCatalog.ToolDefinition definition,
            MorpheusMcpToolService service) {
        McpSchema.Tool tool = McpSchema.Tool.builder(definition.name(), definition.inputSchema())
                .description(definition.description())
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(service, definition.name(), request.arguments()))
                .build();
    }

    private static McpSchema.CallToolResult call(
            MorpheusMcpToolService service,
            String toolName,
            Map<String, Object> arguments) {
        try {
            String result = service.execute(toolName, arguments == null ? Map.of() : arguments);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(result)))
                    .build();
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(safeMessage(expected))))
                    .isError(true)
                    .build();
        }
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
