package com.morpheus.application.orchestration;

import com.morpheus.application.lifecycle.ChangeLifecycleBlocker;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.ConstraintEvaluation;
import com.morpheus.domain.constraint.ConstraintEvaluationState;

import java.util.List;
import java.util.Objects;

/** Stable, non-mutating transition decision intended for JARVIS and other machine consumers. */
public record ChangeTransitionEvaluation(
        ChangeLifecycleState fromState,
        ChangeLifecycleState targetState,
        ChangeTransitionEvaluationState state,
        List<ChangeLifecycleBlocker> blockers,
        List<String> requiredFacts,
        List<String> unavailableRequiredFacts,
        String factSource,
        String reason,
        List<ConstraintEvaluation> constraintEvaluations) {

    /** Compatibility constructor for pre-M16 callers. */
    public ChangeTransitionEvaluation(
            ChangeLifecycleState fromState,
            ChangeLifecycleState targetState,
            ChangeTransitionEvaluationState state,
            List<ChangeLifecycleBlocker> blockers,
            List<String> requiredFacts,
            List<String> unavailableRequiredFacts,
            String factSource,
            String reason) {
        this(
                fromState,
                targetState,
                state,
                blockers,
                requiredFacts,
                unavailableRequiredFacts,
                factSource,
                reason,
                List.of());
    }

    public ChangeTransitionEvaluation {
        Objects.requireNonNull(fromState, "fromState");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(state, "state");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        requiredFacts = List.copyOf(Objects.requireNonNull(requiredFacts, "requiredFacts"));
        unavailableRequiredFacts = List.copyOf(Objects.requireNonNull(unavailableRequiredFacts, "unavailableRequiredFacts"));
        factSource = requireNonBlank(factSource, "factSource");
        reason = requireNonBlank(reason, "reason");
        constraintEvaluations = List.copyOf(Objects.requireNonNull(constraintEvaluations, "constraintEvaluations"));

        if (state == ChangeTransitionEvaluationState.ALLOWED && !blockers.isEmpty()) {
            throw new IllegalArgumentException("ALLOWED transition must not contain blockers");
        }
        if (state == ChangeTransitionEvaluationState.ALLOWED && constraintEvaluations.stream()
                .anyMatch(item -> item.state() == ConstraintEvaluationState.BLOCKING
                        || item.state() == ConstraintEvaluationState.UNKNOWN)) {
            throw new IllegalArgumentException("ALLOWED transition requires all constraint blocking facts to be known and non-blocking");
        }
        if (state == ChangeTransitionEvaluationState.UNKNOWN && unavailableRequiredFacts.isEmpty()) {
            throw new IllegalArgumentException("UNKNOWN transition requires unavailable facts");
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
