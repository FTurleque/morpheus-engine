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
import java.util.Objects;
import java.util.Optional;

/**
 * Metadata-only M22 provider-plugin MCP tool bound to one operator-configured directory.
 * Executable plugin probing is deliberately not model-facing. The default server constructor keeps this surface
 * disabled, so an MCP caller can never choose an arbitrary local directory.
 */
final class MorpheusProviderPluginMcpTools {
    static final String DISCOVER_TOOL = "discover_provider_plugins";
    static final String RETIRED_PROBE_TOOL = "probe_provider_plugin";

    private final ProviderPluginService service = new ProviderPluginService();
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final Optional<Path> pluginDirectory;

    MorpheusProviderPluginMcpTools() {
        this.pluginDirectory = Optional.empty();
    }

    MorpheusProviderPluginMcpTools(Path pluginDirectory) {
        this.pluginDirectory = Optional.of(
                Objects.requireNonNull(pluginDirectory, "pluginDirectory").toAbsolutePath().normalize());
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        if (pluginDirectory.isEmpty()) {
            return List.of();
        }
        return List.of(tool(
                DISCOVER_TOOL,
                "Inspect provider-plugin JAR metadata in the server-configured plugin directory without activating plugin code.",
                schema(Map.of(), List.of())));
    }

    private McpServerFeatures.SyncToolSpecification tool(String name, String description, Map<String, Object> inputSchema) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, inputSchema).description(description).build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(name))
                .build();
    }

    private McpSchema.CallToolResult call(String toolName) {
        try {
            Path configuredDirectory = pluginDirectory.orElseThrow(() ->
                    new IllegalStateException("provider-plugin discovery is not configured for this MCP server"));
            Object result = switch (toolName) {
                case DISCOVER_TOOL -> ProviderPluginViews.remoteDiscovery(service.discover(configuredDirectory));
                default -> throw new IllegalArgumentException("unknown M22 MCP tool: " + toolName);
            };
            return McpSchema.CallToolResult.builder()
                    .addTextContent(json.toJson(result))
                    .isError(false)
                    .build();
        } catch (RuntimeException expected) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(safeMessage(expected))
                    .isError(true)
                    .build();
        }
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

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
