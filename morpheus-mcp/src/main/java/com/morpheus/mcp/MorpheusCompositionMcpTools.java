package com.morpheus.mcp;

import com.morpheus.application.composition.CompositionQueryService;
import com.morpheus.application.composition.CompositionStateView;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.sqlite.SqliteCompositionStateStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** M18 read-only tools exposing persisted multi-provider composition state and conflicts. */
final class MorpheusCompositionMcpTools {
    static final String STATUS_TOOL = "get_composition_status";
    static final String CONFLICTS_TOOL = "list_composition_conflicts";
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final Path databasePath;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusCompositionMcpTools(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(
                        STATUS_TOOL,
                        "Return persisted snapshot-scoped multi-provider composition status for the ACTIVE project snapshot.",
                        schema(false)),
                tool(
                        CONFLICTS_TOOL,
                        "List explicit persisted multi-provider composition conflicts with provenance-preserving candidates.",
                        schema(true)));
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
            try (SqliteSpecificationKnowledgeStore snapshots = new SqliteSpecificationKnowledgeStore(databasePath);
                 SqliteCompositionStateStore compositions = new SqliteCompositionStateStore(databasePath)) {
                CompositionStateView state = new CompositionQueryService(snapshots, compositions)
                        .findActive(projectId)
                        .orElseThrow(() -> new KnowledgeStoreException(
                                "project has no ACTIVE snapshot composition state: " + projectId));
                Object result = switch (toolName) {
                    case STATUS_TOOL -> state;
                    case CONFLICTS_TOOL -> conflicts(state, arguments);
                    default -> throw new IllegalArgumentException("unknown M18 MCP tool: " + toolName);
                };
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(json.toJson(result))))
                        .build();
            }
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(safeMessage(expected))))
                    .isError(true)
                    .build();
        }
    }

    private Object conflicts(CompositionStateView state, Map<String, Object> arguments) {
        int offset = intValue(arguments, "offset", 0, 0, Integer.MAX_VALUE);
        int limit = intValue(arguments, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        int total = state.conflicts().size();
        int from = Math.min(offset, total);
        int to = Math.min(total, from + limit);
        return map(
                "snapshotId", state.snapshotId(),
                "primaryProviderId", state.primaryProviderId(),
                "offset", offset,
                "limit", limit,
                "totalMatches", total,
                "hasMore", to < total,
                "items", state.conflicts().subList(from, to));
    }

    private Map<String, Object> schema(boolean paged) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", Map.of("type", "string", "minLength", 1));
        if (paged) {
            properties.put("offset", Map.of("type", "integer", "minimum", 0));
            properties.put("limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", List.of("projectId"));
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private String requiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must be a non-blank string");
        }
        return text.trim();
    }

    private int intValue(Map<String, Object> arguments, String key, int defaultValue, int minimum, int maximum) {
        Object raw = arguments.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        long value = number.longValue();
        if (Double.compare(number.doubleValue(), (double) value) != 0 || value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be an integer between " + minimum + " and " + maximum);
        }
        return Math.toIntExact(value);
    }

    private Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], Objects.requireNonNull(entries[index + 1], "map value"));
        }
        return Map.copyOf(result);
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
