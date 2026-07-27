package com.morpheus.mcp;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.sdk.provider.ProviderPluginService;
import com.morpheus.sdk.provider.ProviderPluginViews;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit M22 provider-plugin tools. No plugin directory is inspected until one of these tools is called. */
final class MorpheusProviderPluginMcpTools {
    static final String DISCOVER_TOOL = "discover_provider_plugins";
    static final String PROBE_TOOL = "probe_provider_plugin";

    private final ProviderPluginService service = new ProviderPluginService();
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(
                        DISCOVER_TOOL,
                        "Explicitly inspect provider-plugin JAR metadata in one local directory without activating plugin code.",
                        schema(Map.of("directory", stringProperty()), List.of("directory"))),
                tool(
                        PROBE_TOOL,
                        "Explicitly activate one compatible provider plugin and probe one workspace. Plugin failures are returned as diagnostics.",
                        schema(
                                Map.of(
                                        "directory", stringProperty(),
                                        "pluginId", stringProperty(),
                                        "workspace", stringProperty()),
                                List.of("directory", "pluginId", "workspace"))));
    }

    private McpServerFeatures.SyncToolSpecification tool(String name, String description, Map<String, Object> inputSchema) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, inputSchema).description(description).build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(name, request.arguments()))
                .build();
    }

    private McpSchema.CallToolResult call(String toolName, Map<String, Object> rawArguments) {
        try {
            Map<String, Object> arguments = rawArguments == null ? Map.of() : rawArguments;
            Object result = switch (toolName) {
                case DISCOVER_TOOL -> ProviderPluginViews.discovery(service.discover(
                        Path.of(requiredString(arguments, "directory"))));
                case PROBE_TOOL -> service.probe(
                        Path.of(requiredString(arguments, "directory")),
                        requiredString(arguments, "pluginId"),
                        Path.of(requiredString(arguments, "workspace")));
                default -> throw new IllegalArgumentException("unknown M22 MCP tool: " + toolName);
            };
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json.toJson(result))))
                    .build();
        } catch (RuntimeException expected) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(safeMessage(expected))))
                    .isError(true)
                    .build();
        }
    }

    private static Map<String, Object> stringProperty() {
        return Map.of("type", "string", "minLength", 1);
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        result.put("type", "object");
        result.put("properties", Map.copyOf(properties));
        result.put("required", required);
        result.put("additionalProperties", false);
        return Map.copyOf(result);
    }

    private static String requiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("missing required MCP argument: " + key);
        }
        return text.trim();
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
