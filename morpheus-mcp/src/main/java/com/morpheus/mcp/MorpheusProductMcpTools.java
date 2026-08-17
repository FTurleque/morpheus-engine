package com.morpheus.mcp;

import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** M21 product metadata tools. Update discovery remains CLI-only; MCP performs no file or network I/O. */
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
                        "Compatibility stub. URI-backed update discovery is CLI-only and this MCP tool performs no file or network I/O.",
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
        if (INFO_TOOL.equals(toolName)) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(json.toJson(ProductMetadata.current()))
                    .isError(false)
                    .build();
        }
        if (UPDATE_TOOL.equals(toolName)) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("URI-backed update discovery is CLI-only; check_product_update performs no file or network I/O.")
                    .isError(true)
                    .build();
        }
        throw new IllegalArgumentException("unknown M21 MCP tool: " + toolName);
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
}
