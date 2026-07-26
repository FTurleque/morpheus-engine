package com.morpheus.application.lifecycle.mutation;

import java.util.Objects;
import java.util.Optional;

/** Atomic persistence result for lifecycle mutation CAS/idempotency. */
public record ChangeLifecycleMutationPersistenceResult(
        ChangeLifecycleMutationPersistenceState state,
        Optional<ChangeLifecycleOperationalState> lifecycleState,
        Optional<ChangeLifecycleMutationAuditRecord> audit,
        String reason) {

    public ChangeLifecycleMutationPersistenceResult {
        Objects.requireNonNull(state, "state");
        lifecycleState = Objects.requireNonNull(lifecycleState, "lifecycleState");
        audit = Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(reason, "reason");
        reason = reason.trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (state != ChangeLifecycleMutationPersistenceState.CONFLICT
                && (lifecycleState.isEmpty() || audit.isEmpty())) {
            throw new IllegalArgumentException(state + " requires persisted state and audit");
        }
        if (state == ChangeLifecycleMutationPersistenceState.CONFLICT && audit.isPresent()) {
            throw new IllegalArgumentException("CONFLICT must not fabricate an audit record");
        }
    }
}
