package com.morpheus.mcp;

import com.morpheus.application.reasoning.ReasoningContracts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusReasoningMcpToolsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesReadOnlyReasoningIntentSetWithStrictSchemas() {
        var specifications = new MorpheusReasoningMcpTools().specifications();
        assertEquals(2, specifications.size());
        assertEquals(Set.of(
                MorpheusReasoningMcpTools.LIST_TOOL,
                MorpheusReasoningMcpTools.REASON_TOOL),
                specifications.stream().map(item -> item.tool().name()).collect(Collectors.toSet()));
        specifications.forEach(item -> assertEquals(false, item.tool().inputSchema().get("additionalProperties")));
    }

    @Test
    void reasoningSchemaPublishesEvidenceAdapterAndClaimBudgets() {
        Map<String, Object> schema = new MorpheusReasoningMcpTools().specifications().stream()
                .filter(item -> item.tool().name().equals(MorpheusReasoningMcpTools.REASON_TOOL))
                .findFirst().orElseThrow().tool().inputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) properties.get("evidence");
        @SuppressWarnings("unchecked")
        Map<String, Object> adapters = (Map<String, Object>) properties.get("adapterIds");
        @SuppressWarnings("unchecked")
        Map<String, Object> maxClaims = (Map<String, Object>) properties.get("maxClaims");

        assertEquals(ReasoningContracts.MAX_EVIDENCE, evidence.get("maxItems"));
        assertEquals(ReasoningContracts.MAX_ADAPTERS, adapters.get("maxItems"));
        assertEquals(ReasoningContracts.MAX_CLAIMS, maxClaims.get("maximum"));
        assertFalse(properties.containsKey("apply"));
        assertFalse(properties.containsKey("promote"));
        assertFalse(properties.containsKey("activate"));
    }

    @Test
    void serverCatalogAcceptsM27ToolsWithoutCollision() {
        var server = MorpheusMcpServer.build(
                temporaryDirectory.resolve("morpheus.db"),
                java.io.InputStream.nullInputStream(),
                java.io.OutputStream.nullOutputStream());
        try {
            assertTrue(server != null);
        } finally {
            server.close();
        }
    }
}
