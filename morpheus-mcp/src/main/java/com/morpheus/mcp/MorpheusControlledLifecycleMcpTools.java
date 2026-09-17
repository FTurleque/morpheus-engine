package com.morpheus.mcp;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationCommand;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPolicy;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationResultView;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.lifecycle.mutation.ControlledChangeLifecycleMutationService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** M17 write tool, intentionally separate from the M14-M16 read-only orchestration tools. */
final class MorpheusControlledLifecycleMcpTools {
    static final String APPLY_TOOL = "apply_change_lifecycle_transition";

    private final Path databasePath;
    private final ChangeWriteCapabilityResolver writeCapability;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusControlledLifecycleMcpTools(Path databasePath, ChangeWriteCapabilityResolver writeCapability) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.writeCapability = Objects.requireNonNull(writeCapability, "writeCapability");
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        McpSchema.Tool tool = McpSchema.Tool.builder(APPLY_TOOL, schema())
                .description("Apply one controlled MORPHEUS lifecycle transition. This is a side-effecting M17 operation requiring WRITE_CHANGE capability, expectedRevision, idempotencyKey and explicit confirmation.")
                .build();
        return List.of(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(request.arguments()))
                .build());
    }

    private McpSchema.CallToolResult call(Map<String, Object> rawArguments) {
        try {
            Map<String, Object> arguments = McpArguments.orEmpty(rawArguments);
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(McpArguments.requiredString(arguments, "projectId"));
            ChangeId changeId = ChangeId.parse(McpArguments.requiredString(arguments, "changeId"));
            ChangeLifecycleState targetState = lifecycleState(McpArguments.requiredString(arguments, "targetState"));
            long expectedRevision = McpArguments.requiredInteger(arguments, "expectedRevision");
            if (expectedRevision < 0) {
                throw new IllegalArgumentException("expectedRevision must be non-negative");
            }
            ChangeLifecycleMutationId mutationId = McpArguments.optionalString(arguments, "mutationId")
                    .map(ChangeLifecycleMutationId::parse)
                    .orElseGet(ChangeLifecycleMutationId::generate);
            ChangeLifecycleIdempotencyKey idempotencyKey =
                    new ChangeLifecycleIdempotencyKey(McpArguments.requiredString(arguments, "idempotencyKey"));
            String actor = McpArguments.requiredString(arguments, "actor");
            boolean confirmed = McpArguments.requiredBoolean(arguments, "confirmed");
            Optional<ChangeAbandonmentReason> abandonmentReason = abandonmentReason(
                    McpArguments.optionalString(arguments, "abandonmentReason"));

            try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(databasePath)) {
                if (runtime.snapshots.findProject(projectId).isEmpty()) {
                    throw new KnowledgeStoreException("project not found: " + projectId);
                }
                var service = new ControlledChangeLifecycleMutationService(
                        new ChangeTransitionEvaluationService(
                                runtime.snapshots,
                                runtime.content,
                                runtime.requirements,
                                runtime.traceability),
                        runtime.lifecycleMutations,
                        writeCapability);
                var result = service.apply(
                        new ChangeLifecycleMutationCommand(
                                mutationId,
                                idempotencyKey,
                                projectId,
                                changeId,
                                new ChangeLifecycleRevision(expectedRevision),
                                targetState,
                                abandonmentReason,
                                confirmed,
                                actor,
                                Instant.now()),
                        ChangeLifecycleMutationPolicy.strict());
                McpSchema.TextContent content = McpSchema.TextContent.builder(
                                json.toJson(ChangeLifecycleMutationResultView.from(result)))
                        .build();
                return McpSchema.CallToolResult.builder(List.of(content)).build();
            }
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            return McpToolFailure.result(expected);
        }
    }

    private static Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", stringProperty());
        properties.put("changeId", stringProperty());
        properties.put("mutationId", stringProperty());
        properties.put("idempotencyKey", stringProperty());
        properties.put("expectedRevision", Map.of("type", "integer", "minimum", 0));
        properties.put("targetState", Map.of(
                "type", "string",
                "enum", java.util.Arrays.stream(ChangeLifecycleState.values()).map(Enum::name).toList()));
        properties.put("abandonmentReason", Map.of(
                "type", "string",
                "enum", java.util.Arrays.stream(ChangeAbandonmentReason.values()).map(Enum::name).toList()));
        properties.put("actor", stringProperty());
        properties.put("confirmed", Map.of("type", "boolean"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", List.of(
                "projectId", "changeId", "idempotencyKey", "expectedRevision", "targetState", "actor", "confirmed"));
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> stringProperty() {
        return Map.of("type", "string", "minLength", 1);
    }


    private ChangeLifecycleState lifecycleState(String value) {
        try {
            return ChangeLifecycleState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("targetState is not a valid MORPHEUS lifecycle state: " + value, failure);
        }
    }

    private Optional<ChangeAbandonmentReason> abandonmentReason(Optional<String> value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ChangeAbandonmentReason.valueOf(value.orElseThrow().trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("abandonmentReason is invalid: " + value.orElseThrow(), failure);
        }
    }

}
