package com.morpheus.mcp;

import com.morpheus.application.context.AugmentedContextService;
import com.morpheus.application.context.TechnicalContextOptions;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Additive M13 read-only MCP tools for MORPHEUS intent augmented by optional technical context. */
final class MorpheusAugmentedContextMcpTools {
    static final String REQUIREMENT_TOOL = "get_augmented_requirement_context";
    static final String CHANGE_TOOL = "get_augmented_change_context";

    private final Path databasePath;
    private final TechnicalContextProvider provider;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusAugmentedContextMcpTools(Path databasePath, TechnicalContextProvider provider) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(
                        REQUIREMENT_TOOL,
                        "Build live technical context for one ACTIVE requirement. MORPHEUS supplies intent; the external provider owns technical ranking and token budgeting.",
                        schema("requirementId")),
                tool(
                        CHANGE_TOOL,
                        "Build live technical context for one ACTIVE change. MORPHEUS supplies change intent and facts; the external provider owns technical ranking and token budgeting.",
                        schema("changeId")));
    }

    private McpServerFeatures.SyncToolSpecification tool(String name, String description, Map<String, Object> schema) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, schema).description(description).build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(name, request.arguments()))
                .build();
    }

    private McpSchema.CallToolResult call(String toolName, Map<String, Object> rawArguments) {
        try {
            Map<String, Object> arguments = rawArguments == null ? Map.of() : rawArguments;
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(requiredString(arguments, "projectId"));
            TechnicalContextOptions options = options(arguments);
            try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(databasePath)) {
                if (runtime.snapshots.findProject(projectId).isEmpty()) {
                    throw new KnowledgeStoreException("project not found: " + projectId);
                }
                AugmentedContextService service = new AugmentedContextService(
                        runtime.snapshots,
                        runtime.content,
                        runtime.requirements,
                        runtime.traceability,
                        runtime.externalReferences,
                        provider);
                Object result = switch (toolName) {
                    case REQUIREMENT_TOOL -> service.requirement(
                                    projectId,
                                    RequirementId.parse(requiredString(arguments, "requirementId")),
                                    options)
                            .orElseThrow(() -> new KnowledgeStoreException(
                                    "project has no ACTIVE snapshot: " + projectId));
                    case CHANGE_TOOL -> service.change(
                                    projectId,
                                    ChangeId.parse(requiredString(arguments, "changeId")),
                                    options)
                            .orElseThrow(() -> new KnowledgeStoreException(
                                    "project has no ACTIVE snapshot: " + projectId));
                    default -> throw new IllegalArgumentException("unknown M13 MCP tool: " + toolName);
                };
                McpSchema.TextContent content = McpSchema.TextContent.builder(json.toJson(result)).build();
                return McpSchema.CallToolResult.builder(List.of(content)).build();
            }
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            McpSchema.TextContent content = McpSchema.TextContent.builder(safeMessage(expected)).build();
            return McpSchema.CallToolResult.builder(List.of(content))
                    .isError(true)
                    .build();
        }
    }

    private TechnicalContextOptions options(Map<String, Object> arguments) {
        String nexusProject = requiredString(arguments, "nexusProject");
        int tokenBudget = integer(arguments, "tokenBudget", TechnicalContextOptions.DEFAULT_TOKEN_BUDGET);
        Set<String> sources = strings(arguments.get("requestedSources"));
        Map<String, String> constraints = stringMap(arguments.get("constraints"));
        boolean explain = bool(arguments, "explain", false);
        return new TechnicalContextOptions(nexusProject, tokenBudget, sources, constraints, explain);
    }

    private static Map<String, Object> schema(String subjectIdName) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", stringProperty());
        properties.put(subjectIdName, stringProperty());
        properties.put("nexusProject", stringProperty());
        properties.put("tokenBudget", Map.of(
                "type", "integer", "minimum", 1, "maximum", TechnicalContextOptions.MAX_TOKEN_BUDGET));
        properties.put("requestedSources", Map.of(
                "type", "array",
                "uniqueItems", true,
                "items", Map.of("type", "string", "enum", TechnicalContextOptions.ALLOWED_SOURCES.stream().sorted().toList())));
        properties.put("constraints", Map.of(
                "type", "object",
                "additionalProperties", Map.of("type", "string")));
        properties.put("explain", Map.of("type", "boolean"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("projectId", subjectIdName, "nexusProject"));
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> stringProperty() {
        return Map.of("type", "string", "minLength", 1);
    }

    private String requiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("missing required MCP argument: " + key);
        }
        return text.trim();
    }

    private int integer(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        int integer = number.intValue();
        if (Double.compare(number.doubleValue(), integer) != 0) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return integer;
    }

    private Set<String> strings(Object value) {
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("requestedSources must be an array");
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("requestedSources must contain non-blank strings");
            }
            result.add(text.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return Set.copyOf(result);
    }

    private Map<String, String> stringMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("constraints must be an object");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)
                    || !(entry.getValue() instanceof String text)
                    || key.isBlank()
                    || text.isBlank()) {
                throw new IllegalArgumentException("constraints must contain non-blank string keys and values");
            }
            result.put(key.trim(), text.trim());
        }
        return Map.copyOf(result);
    }

    private boolean bool(Map<String, Object> arguments, String key, boolean defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return bool;
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
