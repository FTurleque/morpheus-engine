package com.morpheus.api;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationCommand;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPolicy;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationResultView;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.lifecycle.mutation.ControlledChangeLifecycleMutationService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** HTTP application adapter for M17 explicit lifecycle mutation; routing remains in MorpheusHttpServer. */
final class MorpheusControlledLifecycleApiService {
    private final Path databasePath;
    private final ChangeWriteCapabilityResolver writeCapability;

    MorpheusControlledLifecycleApiService(Path databasePath, ChangeWriteCapabilityResolver writeCapability) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.writeCapability = Objects.requireNonNull(writeCapability, "writeCapability");
    }

    ChangeLifecycleMutationResultView apply(String rawProjectId, String rawChangeId, LifecycleMutationRequest request) {
        Objects.requireNonNull(request, "request");
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(requireNonBlank(rawProjectId, "projectId"));
        ChangeId changeId = ChangeId.parse(requireNonBlank(rawChangeId, "changeId"));
        ChangeLifecycleIdempotencyKey idempotencyKey =
                new ChangeLifecycleIdempotencyKey(requireNonBlank(request.idempotencyKey(), "idempotencyKey"));
        if (request.expectedRevision() == null || request.expectedRevision() < 0) {
            throw new IllegalArgumentException("expectedRevision must be a non-negative integer");
        }
        ChangeLifecycleState targetState = lifecycleState(requireNonBlank(request.targetState(), "targetState"));
        Optional<ChangeAbandonmentReason> abandonmentReason = abandonmentReason(request.abandonmentReason());
        String actor = requireNonBlank(request.actor(), "actor");
        if (request.confirmed() == null) {
            throw new IllegalArgumentException("confirmed is required");
        }
        ChangeLifecycleMutationId mutationId = Optional.ofNullable(request.mutationId())
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(ChangeLifecycleMutationId::parse)
                .orElseGet(ChangeLifecycleMutationId::generate);

        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            if (runtime.snapshots.findProject(projectId).isEmpty()) {
                throw new KnowledgeStoreException("project not found: " + projectId);
            }
            ControlledChangeLifecycleMutationService service = new ControlledChangeLifecycleMutationService(
                    new ChangeTransitionEvaluationService(
                            runtime.snapshots,
                            runtime.content,
                            runtime.requirements,
                            runtime.traceability),
                    runtime.lifecycleMutations,
                    writeCapability);
            return ChangeLifecycleMutationResultView.from(service.apply(
                    new ChangeLifecycleMutationCommand(
                            mutationId,
                            idempotencyKey,
                            projectId,
                            changeId,
                            new ChangeLifecycleRevision(request.expectedRevision()),
                            targetState,
                            abandonmentReason,
                            request.confirmed(),
                            actor,
                            Instant.now()),
                    ChangeLifecycleMutationPolicy.strict()));
        }
    }

    private ChangeLifecycleState lifecycleState(String value) {
        try {
            return ChangeLifecycleState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("targetState is not a valid MORPHEUS lifecycle state: " + value, failure);
        }
    }

    private Optional<ChangeAbandonmentReason> abandonmentReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ChangeAbandonmentReason.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("abandonmentReason is invalid: " + raw, failure);
        }
    }

    /**
     * An omitted field is a client fault, not a server one. This used to call
     * {@link Objects#requireNonNull}, so a request without {@code actor}, {@code idempotencyKey} or
     * {@code targetState} raised a NullPointerException that the server mapped to 500 "internal MORPHEUS API
     * error" -- the same command the CLI and the MCP tool refuse by naming the argument. The blank case already
     * answered 400 and keeps the wording it had.
     */
    private String requireNonBlank(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required and must be a non-blank string");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
