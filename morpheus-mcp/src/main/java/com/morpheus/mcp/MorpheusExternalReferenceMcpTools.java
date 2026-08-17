package com.morpheus.mcp;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.reference.LiveExternalReferenceResolutionService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Additive M12 read-only MCP tools for snapshot-scoped external references. */
final class MorpheusExternalReferenceMcpTools {
    static final String LIST_TOOL = "list_external_references";
    static final String RESOLVE_TOOL = "resolve_external_reference";

    private final Path databasePath;
    private final ExternalReferenceResolverRegistry resolverRegistry;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusExternalReferenceMcpTools(Path databasePath, ExternalReferenceResolverRegistry resolverRegistry) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.resolverRegistry = Objects.requireNonNull(resolverRegistry, "resolverRegistry");
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(LIST_TOOL,
                        "List snapshot-scoped external references owned by one entity in the ACTIVE project snapshot.",
                        schema(List.of("projectId", "ownerId"), Map.of(
                                "projectId", stringId(),
                                "ownerId", stringId()))),
                tool(RESOLVE_TOOL,
                        "Resolve one stored external reference live without mutating the published snapshot.",
                        schema(List.of("projectId", "referenceId"), Map.of(
                                "projectId", stringId(),
                                "referenceId", stringId()))));
    }

    private McpServerFeatures.SyncToolSpecification tool(
            String name,
            String description,
            Map<String, Object> schema) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, schema).description(description).build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(name, request.arguments()))
                .build();
    }

    private McpSchema.CallToolResult call(String toolName, Map<String, Object> arguments) {
        try {
            String result = switch (toolName) {
                case LIST_TOOL -> list(arguments == null ? Map.of() : arguments);
                case RESOLVE_TOOL -> resolve(arguments == null ? Map.of() : arguments);
                default -> throw new IllegalArgumentException("unknown M12 MCP tool: " + toolName);
            };
            return McpSchema.CallToolResult.builder()
                    .addTextContent(result)
                    .isError(false)
                    .build();
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(safeMessage(expected))
                    .isError(true)
                    .build();
        }
    }

    private String list(Map<String, Object> arguments) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(required(arguments, "projectId"));
        DomainIdentity ownerId = DomainIdentity.parse(required(arguments, "ownerId"));
        try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(databasePath)) {
            if (runtime.snapshots.findProject(projectId).isEmpty()) {
                throw new KnowledgeStoreException("project not found: " + projectId);
            }
            List<ExternalReference> references = new LiveExternalReferenceResolutionService(
                    runtime.snapshots, runtime.externalReferences, resolverRegistry, Clock.systemUTC())
                    .listActive(projectId, ownerId)
                    .orElseThrow(() -> new KnowledgeStoreException("project has no ACTIVE snapshot: " + projectId));
            return json.toJson(Map.of(
                    "projectId", projectId.toString(),
                    "ownerId", ownerId.toString(),
                    "items", references.stream().map(this::reference).toList()));
        }
    }

    private String resolve(Map<String, Object> arguments) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(required(arguments, "projectId"));
        ExternalReferenceId referenceId = ExternalReferenceId.parse(required(arguments, "referenceId"));
        try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(databasePath)) {
            if (runtime.snapshots.findProject(projectId).isEmpty()) {
                throw new KnowledgeStoreException("project not found: " + projectId);
            }
            var result = new LiveExternalReferenceResolutionService(
                    runtime.snapshots, runtime.externalReferences, resolverRegistry, Clock.systemUTC())
                    .resolveActive(projectId, referenceId)
                    .orElseThrow(() -> new KnowledgeStoreException("project has no ACTIVE snapshot: " + projectId));
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("snapshotId", result.snapshot().id().toString());
            view.put("stored", reference(result.storedReference()));
            view.put("observed", reference(result.observedReference()));
            view.put("persisted", false);
            return json.toJson(view);
        }
    }

    private Map<String, Object> reference(ExternalReference reference) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("system", reference.target().system());
        target.put("project", reference.target().project().orElse(null));
        target.put("resourceType", reference.target().resourceType());
        target.put("externalId", reference.target().externalId());
        target.put("revision", reference.target().revision().orElse(null));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", reference.id().toString());
        result.put("ownerId", reference.ownerId().toString());
        result.put("target", target);
        result.put("resolutionState", reference.resolutionState().name());
        result.put("resolutionReason", reference.resolutionReason().name());
        result.put("resolvedAttributes", reference.resolvedTarget().map(value -> value.attributes()).orElse(Map.of()));
        result.put("historyCount", reference.history().size());
        return result;
    }

    private String required(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("missing required MCP argument: " + key);
        }
        return text;
    }

    private static Map<String, Object> schema(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> stringId() {
        return Map.of("type", "string", "minLength", 1);
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
