package com.morpheus.integration.minos;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Subprocess test fixture that behaves like the two MINOS MCP tools required by M12. */
public final class FixtureMinosMcpServer {
    private FixtureMinosMcpServer() {
    }

    public static void main(String[] args) throws Exception {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("fixture-minos", "1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .validateToolInputs(true)
                .build();
        server.addTool(tool(MinosMcpCodeGateway.TOOL_INDEX_STATUS, FixtureMinosMcpServer::indexStatus));
        server.addTool(tool(MinosMcpCodeGateway.TOOL_FIND_SYMBOLS, FixtureMinosMcpServer::findSymbols));
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
        McpSchema.Tool tool = McpSchema.Tool.builder(name, schema()).description("M12 fixture tool").build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String text = handler.apply(request.arguments() == null ? Map.of() : request.arguments());
                    return McpSchema.CallToolResult.builder(List.of(McpSchema.TextContent.builder(text).build()))
                            .build();
                })
                .build();
    }

    private static String indexStatus(Map<String, Object> arguments) {
        String project = String.valueOf(arguments.getOrDefault("project", "fixture"));
        return "{\"projectId\":\"project-123\",\"projectName\":\"" + escape(project)
                + "\",\"state\":\"READY\",\"activeSnapshotId\":\"snapshot-abc\","
                + "\"lastSuccessfulIndexAt\":\"2026-07-24T12:00:00Z\","
                + "\"providerId\":\"scip-java\",\"providerVersion\":\"1.7.0\"}";
    }

    private static String findSymbols(Map<String, Object> arguments) {
        String query = String.valueOf(arguments.getOrDefault("query", "symbol:RequirementService"));
        if (query.equals("fixture:too-many")) {
            return "{\"count\":2,\"symbols\":[" + symbol("symbol-one") + "," + symbol("symbol-two") + "]}";
        }
        return "{\"count\":1,\"symbols\":[" + symbol(query) + "]}";
    }

    private static String symbol(String symbolKey) {
        return "{"
                + "\"id\":\"symbol-id\","
                + "\"symbolKey\":\"" + escape(symbolKey) + "\","
                + "\"identityQuality\":\"STABLE\","
                + "\"projectId\":\"project-123\","
                + "\"moduleId\":\"module-main\","
                + "\"fileId\":\"src/main/java/RequirementService.java\","
                + "\"kind\":\"CLASS\","
                + "\"name\":\"RequirementService\","
                + "\"qualifiedName\":\"com.morpheus.RequirementService\","
                + "\"signature\":\"class RequirementService\","
                + "\"language\":\"java\","
                + "\"location\":null,"
                + "\"resolutionStatus\":\"RESOLVED\","
                + "\"origin\":{\"providerId\":\"scip-java\",\"providerType\":\"SCIP\","
                + "\"providerVersion\":\"1.7.0\",\"indexRunId\":\"run-99\",\"sourceType\":\"INDEX\"},"
                + "\"external\":false,\"generated\":false}";
    }

    private static Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("project", Map.of("type", "string"));
        properties.put("query", Map.of("type", "string"));
        properties.put("limit", Map.of("type", "integer"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", true);
        return Map.copyOf(schema);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
