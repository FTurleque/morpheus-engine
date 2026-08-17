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
            Map<String, Object> arguments = rawArguments == null ? Map.of() : rawArguments;
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
            return McpSchema.CallToolResult.builder()
                    .addTextContent(safeMessage(expected))
                    .isError(true)
                    .build();
        }
    }

    private static Request toRequest(Map<String, Object> arguments) {
        rejectUnknown(arguments, List.of("question", "evidence", "adapterIds", "parameters", "maxClaims"));
        String question = requiredString(arguments, "question");
        List<Evidence> evidence = evidence(arguments.get("evidence"));
        List<String> adapterIds = stringList(arguments.get("adapterIds"), "adapterIds");
        Map<String, String> parameters = stringMap(arguments.get("parameters"), "parameters");
        int maxClaims = optionalInteger(arguments.get("maxClaims"), ReasoningContracts.MAX_CLAIMS);
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
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("each evidence item must be an object");
            }
            Map<String, Object> values = stringObjectMap(map, "evidence");
            rejectUnknown(values, List.of("id", "kind", "subject", "statement", "provenance"));
            result.add(new Evidence(
                    requiredString(values, "id"),
                    evidenceKind(requiredString(values, "kind")),
                    requiredString(values, "subject"),
                    requiredString(values, "statement"),
                    stringMap(values.get("provenance"), "provenance")));
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

    private static List<String> stringList(Object raw, String name) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> values)) {
            throw new IllegalArgumentException(name + " must be an array of strings");
        }
        List<String> result = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(name + " must contain non-blank strings");
            }
            result.add(text.trim());
        }
        return List.copyOf(result);
    }

    private static Map<String, String> stringMap(Object raw, String name) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException(name + " must be an object of string values");
        }
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (!(key instanceof String textKey) || textKey.isBlank()
                    || !(value instanceof String textValue) || textValue.isBlank()) {
                throw new IllegalArgumentException(name + " must contain non-blank string keys and values");
            }
            result.put(textKey.trim(), textValue.trim());
        });
        return Map.copyOf(result);
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> raw, String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (!(key instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(name + " keys must be non-blank strings");
            }
            result.put(text, value);
        });
        return Map.copyOf(result);
    }

    private static int optionalInteger(Object raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("maxClaims must be an integer");
        }
        int value = number.intValue();
        if (number.doubleValue() != value) {
            throw new IllegalArgumentException("maxClaims must be an integer");
        }
        return value;
    }

    private static String requiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("missing required MCP argument: " + key);
        }
        return text.trim();
    }

    private static void rejectUnknown(Map<String, ?> values, List<String> allowed) {
        for (String key : values.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("unknown MCP argument: " + key);
            }
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

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
