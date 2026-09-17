package com.morpheus.mcp;

import com.morpheus.application.reasoning.ReasoningContracts;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * The schema tests above assert the declaration. Everything below asserts the behaviour: this class
     * measured 0 of 57 branches while its schema was the most thoroughly verified in the package.
     */
    @Test
    void aMissingQuestionIsRefusedByNameAndSaysWhatWasExpected() {
        McpSchema.CallToolResult result = reason(Map.of());

        assertTrue(result.isError());
        assertEquals("question is required and must be a non-blank string", McpToolCall.text(result));
    }

    @Test
    void aBlankQuestionIsRefusedAsMalformedRatherThanAsAbsent() {
        McpSchema.CallToolResult result = reason(Map.of("question", "   "));

        assertTrue(result.isError());
        assertEquals("question must be a non-blank string", McpToolCall.text(result));
    }

    @Test
    void everyEvidenceFieldTheContractRequiresIsRefusedByNameWhenAbsent() {
        for (String required : List.of("id", "kind", "subject", "statement")) {
            Map<String, Object> item = new LinkedHashMap<>(Map.of(
                    "id", "e1", "kind", "OBSERVATION", "subject", "s", "statement", "the statement"));
            item.remove(required);

            McpSchema.CallToolResult result = reason(Map.of("question", "why", "evidence", List.of(item)));

            assertTrue(result.isError(), () -> required + " must be refused when absent");
            assertEquals(required + " is required and must be a non-blank string", McpToolCall.text(result));
        }
    }

    @Test
    void anUnknownEvidenceKindIsRefusedAndNamesTheValueItRejected() {
        McpSchema.CallToolResult result = reason(Map.of("question", "why", "evidence", List.of(Map.of(
                "id", "e1", "kind", "HEARSAY", "subject", "s", "statement", "the statement"))));

        assertTrue(result.isError());
        assertEquals("invalid evidence kind: HEARSAY", McpToolCall.text(result));
    }

    @Test
    void evidenceOfTheWrongShapeIsRefusedByName() {
        McpSchema.CallToolResult notAnArray = reason(Map.of("question", "why", "evidence", "e1"));
        assertTrue(notAnArray.isError());
        assertEquals("evidence must be an array", McpToolCall.text(notAnArray));

        McpSchema.CallToolResult notObjects = reason(Map.of("question", "why", "evidence", List.of("e1")));
        assertTrue(notObjects.isError());
        assertEquals("evidence must contain objects", McpToolCall.text(notObjects));
    }

    @Test
    void adapterIdsAndParametersAreRefusedByNameWhenTheirShapeIsWrong() {
        McpSchema.CallToolResult adapters = reason(Map.of("question", "why", "adapterIds", "everything"));
        assertTrue(adapters.isError());
        assertEquals("adapterIds must be an array of strings", McpToolCall.text(adapters));

        McpSchema.CallToolResult parameters = reason(Map.of("question", "why", "parameters", List.of("depth")));
        assertTrue(parameters.isError());
        assertEquals("parameters must be an object of string values", McpToolCall.text(parameters));
    }

    @Test
    void aMaxClaimsThatIsNotAWholeNumberIsRefused() {
        McpSchema.CallToolResult result = reason(Map.of("question", "why", "maxClaims", 2.5));

        assertTrue(result.isError());
        assertEquals("maxClaims must be an integer when present", McpToolCall.text(result));
    }

    /**
     * The passing case, and the M27 contract itself: an empty adapter list is not an error, it means facts
     * only. Without this case every refusal above would also pass for a handler that refused unconditionally.
     */
    @Test
    void aWellFormedQuestionWithNoAdaptersAnswersWithFactsOnly() {
        McpSchema.CallToolResult result = reason(Map.of(
                "question", "does the session expire?",
                "adapterIds", List.of(),
                "evidence", List.of(Map.of(
                        "id", "e1", "kind", "PUBLISHED_FACT", "subject", "session",
                        "statement", "The system SHALL expire inactive sessions."))));
        String body = McpToolCall.text(result);

        assertFalse(result.isError(), () -> body);
        assertTrue(body.contains("e1"), () -> "the supplied evidence must be carried through: " + body);
    }

    @Test
    void listingAdaptersNeverActivatesOneAndAnswersWithoutArguments() {
        McpSchema.CallToolResult result = McpToolCall.call(
                new MorpheusReasoningMcpTools().specifications(),
                MorpheusReasoningMcpTools.LIST_TOOL,
                Map.of());

        assertFalse(result.isError(), () -> McpToolCall.text(result));
    }

    private static McpSchema.CallToolResult reason(Map<String, Object> arguments) {
        return McpToolCall.call(
                new MorpheusReasoningMcpTools().specifications(),
                MorpheusReasoningMcpTools.REASON_TOOL,
                arguments);
    }
}
