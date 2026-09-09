package com.morpheus.mcp;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.reasoning.ReasoningContracts;
import com.morpheus.application.reasoning.ReasoningContracts.Evidence;
import com.morpheus.application.reasoning.ReasoningContracts.EvidenceKind;
import com.morpheus.application.reasoning.ReasoningContracts.Request;
import com.morpheus.application.reasoning.ReasoningService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** M27 read-only MCP tools. Adapters are listed and selected explicitly. */
final class MorpheusReasoningMcpTools {
    static final String LIST_TOOL = "list_reasoning_adapters";
    static final String REASON_TOOL = "reason_with_evidence";

    private final ReasoningService service;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusReasoningMcpTools() {
        this(ReasoningService.standard());
    }

    MorpheusReasoningMcpTools(ReasoningService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(
                        LIST_TOOL,
                        "List optional MORPHEUS reasoning adapters. Listing never activates an adapter or performs network access.",
                        schema(Map.of(), List.of())),
                tool(
                        REASON_TOOL,
                        "Produce evidence-backed inferences, heuristics and suggestions without mutating published facts. adapterIds must be explicit; an empty list returns facts only.",
                        reasoningSchema()));
    }

    private McpServerFeatures.SyncToolSpecification tool(
            String name,
            String description,
            Map<String, Object> inputSchema) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, inputSchema).description(description).build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(name, request.arguments()))
                .build();
    }

    private McpSchema.CallToolResult call(String toolName, Map<String, Object> rawArguments) {
        try {
            Map<String, Object> arguments = McpArguments.orEmpty(rawArguments);
            Object result = switch (toolName) {
                case LIST_TOOL -> service.adapters();
                case REASON_TOOL -> service.execute(toRequest(arguments));
                default -> throw new IllegalArgumentException("unknown M27 MCP tool: " + toolName);
            };
            return McpSchema.CallToolResult.builder()
                    .addTextContent(json.toJson(result))
                    .isError(false)
                    .build();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return McpToolFailure.result(expected);
        }
    }

    /**
     * Arguments outside the published schema are refused by the SDK before dispatch (ADR-0102), so this
     * reader states only what the schema cannot: the shape each accepted argument must have.
     */
    private static Request toRequest(Map<String, Object> arguments) {
        String question = McpArguments.requiredString(arguments, "question");
        List<Evidence> evidence = evidence(arguments.get("evidence"));
        List<String> adapterIds = McpArguments.stringList(arguments, "adapterIds");
        Map<String, String> parameters = McpArguments.stringMap(arguments, "parameters");
        int maxClaims = McpArguments.optionalInt(arguments, "maxClaims", ReasoningContracts.MAX_CLAIMS);
        return new Request(question, evidence, adapterIds, parameters, maxClaims);
    }

    private static List<Evidence> evidence(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> items)) {
            throw new IllegalArgumentException("evidence must be an array");
        }
        List<Evidence> result = new ArrayList<>(items.size());
        for (Object item : items) {
            Map<String, Object> values = McpArguments.nestedObject(item, "evidence");
            result.add(new Evidence(
                    McpArguments.requiredString(values, "id"),
                    evidenceKind(McpArguments.requiredString(values, "kind")),
                    McpArguments.requiredString(values, "subject"),
                    McpArguments.requiredString(values, "statement"),
                    McpArguments.stringMap(values, "provenance")));
        }
        return List.copyOf(result);
    }

    private static EvidenceKind evidenceKind(String raw) {
        try {
            return EvidenceKind.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid evidence kind: " + raw, failure);
        }
    }


    private static Map<String, Object> reasoningSchema() {
        Map<String, Object> evidenceItem = schema(
                Map.of(
                        "id", Map.of("type", "string", "minLength", 1, "maxLength", 128),
                        "kind", Map.of("type", "string", "enum", List.of(
                                "PUBLISHED_FACT", "SOURCE_EXCERPT", "POLICY_RESULT", "EXTERNAL_CONTEXT", "OBSERVATION")),
                        "subject", Map.of("type", "string", "minLength", 1, "maxLength", 512),
                        "statement", Map.of("type", "string", "minLength", 1, "maxLength", ReasoningContracts.MAX_STATEMENT_CHARS),
                        "provenance", Map.of("type", "object", "additionalProperties", Map.of("type", "string"),
                                "maxProperties", ReasoningContracts.MAX_PROVENANCE_ENTRIES)),
                List.of("id", "kind", "subject", "statement"));
        return schema(
                Map.of(
                        "question", Map.of("type", "string", "minLength", 1,
                                "maxLength", ReasoningContracts.MAX_QUESTION_CHARS),
                        "evidence", Map.of("type", "array", "maxItems", ReasoningContracts.MAX_EVIDENCE,
                                "items", evidenceItem),
                        "adapterIds", Map.of("type", "array", "maxItems", ReasoningContracts.MAX_ADAPTERS,
                                "uniqueItems", true, "items", Map.of("type", "string", "minLength", 1, "maxLength", 128)),
                        "parameters", Map.of("type", "object", "maxProperties", ReasoningContracts.MAX_PARAMETER_ENTRIES,
                                "additionalProperties", Map.of("type", "string")),
                        "maxClaims", Map.of("type", "integer", "minimum", 1, "maximum", ReasoningContracts.MAX_CLAIMS)),
                List.of("question"));
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
