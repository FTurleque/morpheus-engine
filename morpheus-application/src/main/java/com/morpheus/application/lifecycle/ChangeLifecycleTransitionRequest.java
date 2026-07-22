package com.morpheus.application.lifecycle;

import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;

import java.util.Objects;
import java.util.Optional;

/** Input for deterministic lifecycle transition validation. */
public record ChangeLifecycleTransitionRequest(
        ChangeLifecycle source,
        ChangeLifecycleState targetState,
        ChangeLifecycleFacts facts,
        ChangeLifecyclePolicy policy,
        Optional<ChangeAbandonmentReason> abandonmentReason) {

    public ChangeLifecycleTransitionRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(policy, "policy");
        abandonmentReason = Objects.requireNonNull(abandonmentReason, "abandonmentReason");
    }

    public static ChangeLifecycleTransitionRequest to(
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            ChangeLifecycleFacts facts,
            ChangeLifecyclePolicy policy) {
        return new ChangeLifecycleTransitionRequest(source, targetState, facts, policy, Optional.empty());
    }
}
