package com.morpheus.integration.nexus;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Subprocess fixture reproducing the three NEXUS MCP tools required by M13. */
public final class FixtureNexusMcpServer {
    private FixtureNexusMcpServer() {
    }

    public static void main(String[] args) {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("fixture-nexus", "1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .validateToolInputs(true)
                .build();
        server.addTool(tool(NexusMcpContextGateway.TOOL_LIST_PROJECTS, FixtureNexusMcpServer::projects));
        server.addTool(tool(NexusMcpContextGateway.TOOL_BUILD_CONTEXT, argsMap -> context(argsMap, false)));
        server.addTool(tool(NexusMcpContextGateway.TOOL_EXPLAIN_CONTEXT, argsMap -> context(argsMap, true)));
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
        McpSchema.Tool tool = McpSchema.Tool.builder(name, schema()).description("M13 fixture tool").build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String text = handler.apply(request.arguments() == null ? Map.of() : request.arguments());
                    return McpSchema.CallToolResult.builder(List.of(McpSchema.TextContent.builder(text).build()))
                            .build();
                })
                .build();
    }

    private static String projects(Map<String, Object> ignored) {
        return "[{\"id\":\"nexus-project-id\",\"name\":\"morpheus-engine\"," +
                "\"rootPath\":\"N:/workspace-dev/morpheus-engine\",\"sourceType\":\"LOCAL\"," +
                "\"languages\":[\"java\"],\"technologies\":[\"maven\"]," +
                "\"lastIndexedAt\":\"2026-07-24T12:00:00Z\",\"indexStatus\":\"READY\"}]";
    }

    private static String context(Map<String, Object> arguments, boolean explain) {
        String project = escape(String.valueOf(arguments.getOrDefault("project", "morpheus-engine")));
        String query = escape(String.valueOf(arguments.getOrDefault("query", "intent")));
        int budget = arguments.get("tokenBudget") instanceof Number number ? number.intValue() : 2000;
        return "{"
                + "\"project\":{\"id\":\"nexus-project-id\",\"name\":\"" + project + "\","
                + "\"rootPath\":\"N:/workspace-dev/morpheus-engine\",\"sourceType\":\"LOCAL\","
                + "\"languages\":[\"java\"],\"technologies\":[\"maven\"],\"lastIndexedAt\":null,\"indexStatus\":\"READY\"},"
                + "\"query\":\"" + query + "\",\"explain\":" + explain + ",\"durationMs\":7,"
                + "\"tokenBudget\":" + budget + ",\"estimatedTokens\":111,"
                + "\"items\":[{\"type\":\"SYMBOL\",\"path\":\"src/main/java/SessionService.java\","
                + "\"symbol\":\"SessionService\",\"startLine\":10,\"endLine\":20,"
                + "\"content\":\"class SessionService {}\",\"score\":0.91,"
                + "\"scoreComponents\":{\"lexical\":0.41,\"structural\":0.5},"
                + "\"reasons\":[\"intent match\"],\"estimatedTokens\":111,\"truncated\":false}],"
                + "\"excluded\":[\"target/generated.txt\"],"
                + "\"metadata\":{\"strategy\":\"hybrid\",\"requestedSourcesEcho\":\""
                + escape(String.valueOf(arguments.getOrDefault("requestedSources", List.of()))) + "\"}}";
    }

    private static Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("project", Map.of("type", "string"));
        properties.put("query", Map.of("type", "string"));
        properties.put("tokenBudget", Map.of("type", "integer"));
        properties.put("requestedSources", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("constraints", Map.of("type", "object", "additionalProperties", Map.of("type", "string")));
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
