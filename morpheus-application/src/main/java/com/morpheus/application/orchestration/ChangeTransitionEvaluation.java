package com.morpheus.application.orchestration;

import com.morpheus.application.lifecycle.ChangeLifecycleBlocker;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;

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
        String reason) {

    public ChangeTransitionEvaluation {
        Objects.requireNonNull(fromState, "fromState");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(state, "state");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        requiredFacts = List.copyOf(Objects.requireNonNull(requiredFacts, "requiredFacts"));
        unavailableRequiredFacts = List.copyOf(Objects.requireNonNull(unavailableRequiredFacts, "unavailableRequiredFacts"));
        factSource = requireNonBlank(factSource, "factSource");
        reason = requireNonBlank(reason, "reason");

        if (state == ChangeTransitionEvaluationState.ALLOWED && !blockers.isEmpty()) {
            throw new IllegalArgumentException("ALLOWED transition must not contain blockers");
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
