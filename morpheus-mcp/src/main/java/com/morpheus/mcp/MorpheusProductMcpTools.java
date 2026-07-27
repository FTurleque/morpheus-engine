package com.morpheus.mcp;

import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.product.UpdateDiscoveryService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** M21 read-only product integrity tools. No I/O occurs unless check_product_update is invoked explicitly. */
final class MorpheusProductMcpTools {
    static final String INFO_TOOL = "get_product_info";
    static final String UPDATE_TOOL = "check_product_update";

    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(
                        INFO_TOOL,
                        "Return MORPHEUS product/build metadata. This tool performs no network access.",
                        schema(Map.of(), List.of())),
                tool(
                        UPDATE_TOOL,
                        "Explicitly read one release manifest and report whether a newer MORPHEUS version is available. This tool never downloads or installs an update.",
                        schema(Map.of("manifestUri", Map.of("type", "string", "minLength", 1)), List.of("manifestUri"))));
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
                case INFO_TOOL -> ProductMetadata.current();
                case UPDATE_TOOL -> new UpdateDiscoveryService().check(
                        URI.create(requiredString(arguments, "manifestUri")));
                default -> throw new IllegalArgumentException("unknown M21 MCP tool: " + toolName);
            };
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json.toJson(result))))
                    .build();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(safeMessage(expected))))
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
