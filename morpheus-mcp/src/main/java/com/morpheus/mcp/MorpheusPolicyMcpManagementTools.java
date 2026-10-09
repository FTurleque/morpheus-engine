package com.morpheus.mcp;

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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Read-only M25 management surface for policy activation/CAS state discovery. */
final class MorpheusPolicyMcpManagementTools {
    static final String LIST_ACTIVATIONS = "list_policy_activations";

    private final Path databasePath;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusPolicyMcpManagementTools(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(tool(
                LIST_ACTIVATIONS,
                "List active policy versions and CAS revisions for one explicit scope.",
                scopeSchema()));
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
            Map<String, Object> arguments = McpArguments.orEmpty(rawArguments);
            try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(databasePath)) {
                PolicyPackService registry = new PolicyPackService(store);
                Object result = switch (name) {
                    case LIST_ACTIVATIONS -> PolicyPublicViews.activations(registry.activations(scope(arguments)));
                    default -> throw new IllegalArgumentException("unknown M25 policy management tool: " + name);
                };
                return McpSchema.CallToolResult.builder()
                        .addTextContent(json.toJson(result))
                        .isError(false)
                        .build();
            }
        } catch (IllegalArgumentException | IllegalStateException | KnowledgeStoreException expected) {
            return McpToolFailure.result(expected);
        }
    }

    private PolicyScope scope(Map<String, Object> arguments) {
        String kind = McpArguments.requiredString(arguments, "scopeKind").toUpperCase(Locale.ROOT);
        String id = McpArguments.requiredString(arguments, "scopeId");
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


}
