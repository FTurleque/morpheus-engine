package com.morpheus.application.lifecycle.mutation;

import java.util.Objects;
import java.util.Optional;

/** Explainable result of a controlled lifecycle mutation request. */
public record ChangeLifecycleMutationResult(
        ChangeLifecycleMutationResultState state,
        Optional<ChangeLifecycleOperationalState> lifecycleState,
        Optional<ChangeLifecycleMutationAuditRecord> audit,
        String reason) {

    public ChangeLifecycleMutationResult {
        Objects.requireNonNull(state, "state");
        lifecycleState = Objects.requireNonNull(lifecycleState, "lifecycleState");
        audit = Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(reason, "reason");
        reason = reason.trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if ((state == ChangeLifecycleMutationResultState.APPLIED
                || state == ChangeLifecycleMutationResultState.ALREADY_APPLIED)
                && (lifecycleState.isEmpty() || audit.isEmpty())) {
            throw new IllegalArgumentException(state + " requires lifecycle state and audit");
        }
        if (state != ChangeLifecycleMutationResultState.APPLIED
                && state != ChangeLifecycleMutationResultState.ALREADY_APPLIED
                && audit.isPresent()) {
            throw new IllegalArgumentException(state + " must not fabricate an applied audit record");
        }
    }

    public static ChangeLifecycleMutationResult simple(ChangeLifecycleMutationResultState state, String reason) {
        return new ChangeLifecycleMutationResult(state, Optional.empty(), Optional.empty(), reason);
    }
}
