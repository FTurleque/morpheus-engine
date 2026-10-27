package com.morpheus.mcp;

import com.morpheus.application.composition.CompositionQueryService;
import com.morpheus.application.composition.CompositionStateView;
import com.morpheus.application.foundation.PayloadMap;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.project.ProjectSpecificationId;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** M18 read-only tools exposing persisted multi-provider composition state and conflicts. */
public final class MorpheusCompositionMcpTools {
    static final String STATUS_TOOL = "get_composition_status";
    static final String CONFLICTS_TOOL = "list_composition_conflicts";

    private final Path databasePath;
    private final OperationalRecorder recorder;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    public MorpheusCompositionMcpTools(Path databasePath, OperationalRecorder recorder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
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
            Map<String, Object> arguments = McpArguments.orEmpty(rawArguments);
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(McpArguments.requiredString(arguments, "projectId"));
            try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(databasePath, recorder)) {
                CompositionStateView state = new CompositionQueryService(runtime.snapshots(), runtime.compositions())
                        .findActive(projectId)
                        .orElseThrow(() -> new KnowledgeStoreException(
                                "project has no ACTIVE snapshot composition state: " + projectId));
                Object result = switch (toolName) {
                    case STATUS_TOOL -> state;
                    case CONFLICTS_TOOL -> conflicts(state, arguments);
                    default -> throw new IllegalArgumentException("unknown M18 MCP tool: " + toolName);
                };
                McpSchema.TextContent content = McpSchema.TextContent.builder(json.toJson(result)).build();
                return McpSchema.CallToolResult.builder(List.of(content)).isError(false).build();
            }
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            return McpToolFailure.result(expected);
        }
    }

    private Object conflicts(CompositionStateView state, Map<String, Object> arguments) {
        return PagedEnvelope.following(
                PayloadMap.of("snapshotId", state.snapshotId(), "primaryProviderId", state.primaryProviderId()),
                PageArguments.slice(arguments, state::conflicts, Function.identity()));
    }

    private Map<String, Object> schema(boolean paged) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", Map.of("type", "string", "minLength", 1));
        if (paged) {
            properties.putAll(PageArguments.properties());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", List.of("projectId"));
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }


}
