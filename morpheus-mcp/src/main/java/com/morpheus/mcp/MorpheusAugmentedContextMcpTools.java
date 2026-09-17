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
            Map<String, Object> arguments = McpArguments.orEmpty(rawArguments);
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(McpArguments.requiredString(arguments, "projectId"));
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
                                    RequirementId.parse(McpArguments.requiredString(arguments, "requirementId")),
                                    options)
                            .orElseThrow(() -> new KnowledgeStoreException(
                                    "project has no ACTIVE snapshot: " + projectId));
                    case CHANGE_TOOL -> service.change(
                                    projectId,
                                    ChangeId.parse(McpArguments.requiredString(arguments, "changeId")),
                                    options)
                            .orElseThrow(() -> new KnowledgeStoreException(
                                    "project has no ACTIVE snapshot: " + projectId));
                    default -> throw new IllegalArgumentException("unknown M13 MCP tool: " + toolName);
                };
                McpSchema.TextContent content = McpSchema.TextContent.builder(json.toJson(result)).build();
                return McpSchema.CallToolResult.builder(List.of(content)).build();
            }
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            return McpToolFailure.result(expected);
        }
    }

    private TechnicalContextOptions options(Map<String, Object> arguments) {
        String nexusProject = McpArguments.requiredString(arguments, "nexusProject");
        int tokenBudget = McpArguments.optionalInt(
                arguments, "tokenBudget", TechnicalContextOptions.DEFAULT_TOKEN_BUDGET);
        Set<String> sources = upperCased(McpArguments.stringList(arguments, "requestedSources"));
        Map<String, String> constraints = McpArguments.stringMap(arguments, "constraints");
        boolean explain = McpArguments.optionalBoolean(arguments, "explain", false);
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

    /** The source vocabulary is case-insensitive on this surface and normalised through the root locale. */
    private Set<String> upperCased(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(value.toUpperCase(java.util.Locale.ROOT));
        }
        return Set.copyOf(result);
    }

}
