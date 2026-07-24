package com.morpheus.api;

import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.orchestration.ChangeLifecycleObservation;
import com.morpheus.application.orchestration.ChangeOrchestrationStateService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** M14 HTTP facade for the read-only MORPHEUS/JARVIS orchestration contract. */
final class MorpheusJarvisOrchestrationApiService {
    private final Path databasePath;

    MorpheusJarvisOrchestrationApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    Object state(
            String projectIdValue,
            String changeIdValue,
            Optional<String> lifecycleState,
            Optional<String> abandonmentReason) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        ChangeLifecycleObservation lifecycle = lifecycle(lifecycleState, abandonmentReason);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            return new ChangeOrchestrationStateService(
                            runtime.snapshots,
                            runtime.content,
                            runtime.requirements,
                            runtime.traceability,
                            runtime.externalReferences)
                    .active(projectId, changeId, lifecycle)
                    .orElseThrow(() -> new KnowledgeStoreException(
                            "project has no ACTIVE snapshot: " + projectId));
        }
    }

    Object transition(String projectIdValue, String changeIdValue, TransitionCheckRequest request) {
        Objects.requireNonNull(request, "request");
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        ChangeLifecycleState from = lifecycleState(request.fromState(), "fromState");
        ChangeLifecycleState target = lifecycleState(request.targetState(), "targetState");
        Optional<ChangeAbandonmentReason> fromReason = abandonmentReason(request.fromAbandonmentReason(), "fromAbandonmentReason");
        Optional<ChangeAbandonmentReason> targetReason = abandonmentReason(request.abandonmentReason(), "abandonmentReason");
        ChangeLifecycle source = from == ChangeLifecycleState.ABANDONED
                ? ChangeLifecycle.abandoned(changeId, fromReason.orElseThrow(() ->
                        new IllegalArgumentException("fromAbandonmentReason is required when fromState=ABANDONED")))
                : ChangeLifecycle.of(changeId, from);
        if (from != ChangeLifecycleState.ABANDONED && fromReason.isPresent()) {
            throw new IllegalArgumentException("fromAbandonmentReason is only valid when fromState=ABANDONED");
        }
        ChangeLifecyclePolicy policy = new ChangeLifecyclePolicy(
                Boolean.TRUE.equals(request.allowBackwardTransitions()),
                Boolean.TRUE.equals(request.allowCompletedReopen()));

        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            if (runtime.snapshots.findProject(projectId).isEmpty()) {
                throw new KnowledgeStoreException("project not found: " + projectId);
            }
            return new ChangeTransitionEvaluationService(
                            runtime.snapshots,
                            runtime.content,
                            runtime.requirements,
                            runtime.traceability)
                    .evaluateActive(projectId, source, target, policy, targetReason)
                    .orElseThrow(() -> new KnowledgeStoreException(
                            "project has no ACTIVE snapshot: " + projectId));
        }
    }

    private ChangeLifecycleObservation lifecycle(
            Optional<String> lifecycleState,
            Optional<String> abandonmentReason) {
        Objects.requireNonNull(lifecycleState, "lifecycleState");
        Objects.requireNonNull(abandonmentReason, "abandonmentReason");
        if (lifecycleState.isEmpty()) {
            if (abandonmentReason.isPresent()) {
                throw new IllegalArgumentException("abandonmentReason requires lifecycleState=ABANDONED");
            }
            return ChangeLifecycleObservation.unavailable();
        }
        ChangeLifecycleState state = lifecycleState(lifecycleState.orElseThrow(), "lifecycleState");
        Optional<ChangeAbandonmentReason> reason = abandonmentReason(abandonmentReason.orElse(null), "abandonmentReason");
        return ChangeLifecycleObservation.callerSupplied(state, reason);
    }

    private ChangeLifecycleState lifecycleState(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        try {
            return ChangeLifecycleState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(name + " is not a valid MORPHEUS lifecycle state: " + value, failure);
        }
    }

    private Optional<ChangeAbandonmentReason> abandonmentReason(String value, String name) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ChangeAbandonmentReason.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(name + " is not a valid MORPHEUS abandonment reason: " + value, failure);
        }
    }
}
