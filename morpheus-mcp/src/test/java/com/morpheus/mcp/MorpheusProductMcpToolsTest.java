package com.morpheus.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.product.ProductMetadata;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MorpheusProductMcpToolsTest {
    @Test
    void mcpServerVersionComesFromSharedProductMetadata() {
        assertEquals(ProductMetadata.version(), MorpheusMcpServer.SERVER_VERSION);
    }

    @Test
    void exposesExplicitReadOnlyProductTools() {
        var specifications = new MorpheusProductMcpTools().specifications();
        Set<String> names = specifications.stream()
                .map(specification -> specification.tool().name())
                .collect(Collectors.toSet());

        assertEquals(2, specifications.size());
        assertTrue(names.contains(MorpheusProductMcpTools.INFO_TOOL));
        assertTrue(names.contains(MorpheusProductMcpTools.UPDATE_TOOL));
    }

    /** Both handlers were declared and never called; the second one exists only to refuse. */
    @Test
    void productInfoAnswersFromSharedMetadataWithoutTouchingTheFilesystem() {
        McpSchema.CallToolResult result = McpToolCall.call(
                new MorpheusProductMcpTools().specifications(),
                MorpheusProductMcpTools.INFO_TOOL,
                Map.of());
        String body = McpToolCall.text(result);

        assertFalse(result.isError(), () -> body);
        assertTrue(body.contains(ProductMetadata.version()), () -> body);
    }

    /**
     * URI-backed update discovery is CLI-only. The MCP tool exists so the capability is not silently absent
     * from the transport, and it must answer with an explicit refusal rather than a plausible-looking result.
     */
    @Test
    void updateDiscoveryRefusesOnMcpInsteadOfPerformingFileOrNetworkIo() {
        McpSchema.CallToolResult result = McpToolCall.call(
                new MorpheusProductMcpTools().specifications(),
                MorpheusProductMcpTools.UPDATE_TOOL,
                Map.of());
        String body = McpToolCall.text(result);

        assertTrue(result.isError(), () -> body);
        assertTrue(body.contains("CLI-only"), () -> body);
        assertTrue(body.contains("no file or network I/O"), () -> body);
    }
}
