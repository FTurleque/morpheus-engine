package com.morpheus.mcp;

import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.orchestration.ChangeLifecycleObservation;
import com.morpheus.application.orchestration.ChangeOrchestrationStateService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Additive M14 read-only tools exposing lifecycle facts and transition decisions to orchestrators. */
final class MorpheusJarvisOrchestrationMcpTools {
    static final String STATE_TOOL = "get_change_orchestration_state";
    static final String TRANSITION_TOOL = "evaluate_change_transition";

    private final Path databasePath;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusJarvisOrchestrationMcpTools(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(
                        STATE_TOOL,
                        "Return the read-only UC-16 orchestration state for one ACTIVE change. Lifecycle is never inferred; supply lifecycleState only when explicitly observed.",
                        stateSchema()),
                tool(
                        TRANSITION_TOOL,
                        "Evaluate one MORPHEUS lifecycle transition without mutating the change, provider, or snapshot.",
                        transitionSchema()));
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
            ChangeId changeId = ChangeId.parse(requiredString(arguments, "changeId"));
            try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(databasePath)) {
                if (runtime.snapshots.findProject(projectId).isEmpty()) {
                    throw new KnowledgeStoreException("project not found: " + projectId);
                }
                Object result = switch (toolName) {
                    case STATE_TOOL -> new ChangeOrchestrationStateService(
                                    runtime.snapshots,
                                    runtime.content,
                                    runtime.requirements,
                                    runtime.traceability,
                                    runtime.externalReferences)
                            .active(projectId, changeId, observation(arguments))
                            .orElseThrow(() -> new KnowledgeStoreException(
                                    "project has no ACTIVE snapshot: " + projectId));
                    case TRANSITION_TOOL -> transition(runtime, projectId, changeId, arguments);
                    default -> throw new IllegalArgumentException("unknown M14 MCP tool: " + toolName);
                };
                return McpSchema.CallToolResult.builder()
                        .addTextContent(json.toJson(result))
                        .isError(false)
                        .build();
            }
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(safeMessage(expected))
                    .isError(true)
                    .build();
        }
    }

    private Object transition(
            MorpheusMcpRuntime runtime,
            ProjectSpecificationId projectId,
            ChangeId changeId,
            Map<String, Object> arguments) {
        ChangeLifecycleState from = lifecycleState(requiredString(arguments, "fromState"), "fromState");
        ChangeLifecycleState target = lifecycleState(requiredString(arguments, "targetState"), "targetState");
        Optional<ChangeAbandonmentReason> fromReason = abandonmentReason(optionalString(arguments, "fromAbandonmentReason"), "fromAbandonmentReason");
        Optional<ChangeAbandonmentReason> targetReason = abandonmentReason(optionalString(arguments, "abandonmentReason"), "abandonmentReason");
        ChangeLifecycle source = from == ChangeLifecycleState.ABANDONED
                ? ChangeLifecycle.abandoned(changeId, fromReason.orElseThrow(() ->
                        new IllegalArgumentException("fromAbandonmentReason is required when fromState=ABANDONED")))
                : ChangeLifecycle.of(changeId, from);
        if (from != ChangeLifecycleState.ABANDONED && fromReason.isPresent()) {
            throw new IllegalArgumentException("fromAbandonmentReason is only valid when fromState=ABANDONED");
        }
        ChangeLifecyclePolicy policy = new ChangeLifecyclePolicy(
                bool(arguments, "allowBackwardTransitions", false),
                bool(arguments, "allowCompletedReopen", false));
        return new ChangeTransitionEvaluationService(
                        runtime.snapshots,
                        runtime.content,
                        runtime.requirements,
                        runtime.traceability)
                .evaluateActive(projectId, source, target, policy, targetReason)
                .orElseThrow(() -> new KnowledgeStoreException(
                        "project has no ACTIVE snapshot: " + projectId));
    }

    private ChangeLifecycleObservation observation(Map<String, Object> arguments) {
        Optional<String> rawState = optionalString(arguments, "lifecycleState");
        Optional<String> rawReason = optionalString(arguments, "abandonmentReason");
        if (rawState.isEmpty()) {
            if (rawReason.isPresent()) {
                throw new IllegalArgumentException("abandonmentReason requires lifecycleState=ABANDONED");
            }
            return ChangeLifecycleObservation.unavailable();
        }
        return ChangeLifecycleObservation.callerSupplied(
                lifecycleState(rawState.orElseThrow(), "lifecycleState"),
                abandonmentReason(rawReason, "abandonmentReason"));
    }

    private static Map<String, Object> stateSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", stringProperty());
        properties.put("changeId", stringProperty());
        properties.put("lifecycleState", lifecycleStateProperty());
        properties.put("abandonmentReason", abandonmentReasonProperty());
        return schema(properties, List.of("projectId", "changeId"));
    }

    private static Map<String, Object> transitionSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", stringProperty());
        properties.put("changeId", stringProperty());
        properties.put("fromState", lifecycleStateProperty());
        properties.put("fromAbandonmentReason", abandonmentReasonProperty());
        properties.put("targetState", lifecycleStateProperty());
        properties.put("abandonmentReason", abandonmentReasonProperty());
        properties.put("allowBackwardTransitions", Map.of("type", "boolean"));
        properties.put("allowCompletedReopen", Map.of("type", "boolean"));
        return schema(properties, List.of("projectId", "changeId", "fromState", "targetState"));
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> stringProperty() {
        return Map.of("type", "string", "minLength", 1);
    }

    private static Map<String, Object> lifecycleStateProperty() {
        return Map.of(
                "type", "string",
                "enum", java.util.Arrays.stream(ChangeLifecycleState.values()).map(Enum::name).toList());
    }

    private static Map<String, Object> abandonmentReasonProperty() {
        return Map.of(
                "type", "string",
                "enum", java.util.Arrays.stream(ChangeAbandonmentReason.values()).map(Enum::name).toList());
    }

    private String requiredString(Map<String, Object> arguments, String key) {
        return optionalString(arguments, key)
                .orElseThrow(() -> new IllegalArgumentException("missing required MCP argument: " + key));
    }

    private Optional<String> optionalString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must be a non-blank string");
        }
        return Optional.of(text.trim());
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

    private ChangeLifecycleState lifecycleState(String value, String name) {
        try {
            return ChangeLifecycleState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(name + " is not a valid MORPHEUS lifecycle state: " + value, failure);
        }
    }

    private Optional<ChangeAbandonmentReason> abandonmentReason(Optional<String> value, String name) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ChangeAbandonmentReason.valueOf(value.orElseThrow().trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(name + " is not a valid MORPHEUS abandonment reason: " + value.orElseThrow(), failure);
        }
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
