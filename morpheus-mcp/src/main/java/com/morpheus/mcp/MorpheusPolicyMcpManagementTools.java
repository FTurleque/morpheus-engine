package com.morpheus.mcp;

import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyPublicViews;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.sqlite.SqlitePolicyPackStore;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Small M25 management surface for CAS state discovery and audited override removal. */
final class MorpheusPolicyMcpManagementTools {
    static final String LIST_ACTIVATIONS = "list_policy_activations";
    static final String REMOVE_OVERRIDE = "remove_policy_override";

    private final Path databasePath;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusPolicyMcpManagementTools(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(LIST_ACTIVATIONS, "List active policy versions and CAS revisions for one explicit scope.", scopeSchema()),
                tool(REMOVE_OVERRIDE, "CAS-remove one policy override with actor/reason audit.", removeOverrideSchema()));
    }

    private McpServerFeatures.SyncToolSpecification tool(String name, String description, Map<String, Object> schema) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, schema).description(description).build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(name, request.arguments()))
                .build();
    }

    private McpSchema.CallToolResult call(String name, Map<String, Object> rawArguments) {
        try {
            Map<String, Object> arguments = rawArguments == null ? Map.of() : rawArguments;
            try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(databasePath)) {
                PolicyPackService registry = new PolicyPackService(store);
                Object result = switch (name) {
                    case LIST_ACTIVATIONS -> PolicyPublicViews.activations(registry.activations(scope(arguments)));
                    case REMOVE_OVERRIDE -> {
                        registry.removeOverride(
                                scope(arguments),
                                PolicyIds.PackId.parse(requiredString(arguments, "id")),
                                PolicyIds.RuleId.parse(requiredString(arguments, "ruleId")),
                                longValue(arguments, "expectedRevision", 1, Long.MAX_VALUE),
                                requiredString(arguments, "actor"),
                                requiredString(arguments, "reason"));
                        yield Map.of("removed", true);
                    }
                    default -> throw new IllegalArgumentException("unknown M25 policy management tool: " + name);
                };
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(json.toJson(result))))
                        .build();
            }
        } catch (IllegalArgumentException | IllegalStateException | KnowledgeStoreException expected) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(safeMessage(expected))))
                    .isError(true)
                    .build();
        }
    }

    private PolicyScope scope(Map<String, Object> arguments) {
        String kind = requiredString(arguments, "scopeKind").toUpperCase();
        String id = requiredString(arguments, "scopeId");
        return switch (kind) {
            case "PROJECT" -> new PolicyScope.Project(ProjectSpecificationId.parse(id));
            case "PORTFOLIO" -> new PolicyScope.Portfolio(PortfolioId.parse(id));
            default -> throw new IllegalArgumentException("scopeKind must be PROJECT or PORTFOLIO");
        };
    }

    private static Map<String, Object> scopeSchema() {
        return schema(
                List.of("scopeKind", "scopeId"),
                Map.of(
                        "scopeKind", Map.of("type", "string", "enum", List.of("PROJECT", "PORTFOLIO")),
                        "scopeId", nonBlankString()));
    }

    private static Map<String, Object> removeOverrideSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", nonBlankString());
        properties.put("ruleId", nonBlankString());
        properties.put("scopeKind", Map.of("type", "string", "enum", List.of("PROJECT", "PORTFOLIO")));
        properties.put("scopeId", nonBlankString());
        properties.put("expectedRevision", Map.of("type", "integer", "minimum", 1));
        properties.put("actor", nonBlankString());
        properties.put("reason", nonBlankString());
        return schema(
                List.of("id", "ruleId", "scopeKind", "scopeId", "expectedRevision", "actor", "reason"),
                properties);
    }

    private static Map<String, Object> schema(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> nonBlankString() {
        return Map.of("type", "string", "minLength", 1);
    }

    private static String requiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must be a non-blank string");
        }
        return text.trim();
    }

    private static long longValue(Map<String, Object> arguments, String key, long minimum, long maximum) {
        Object raw = arguments.get(key);
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        long value = number.longValue();
        if (Double.compare(number.doubleValue(), (double) value) != 0 || value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be an integer between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}