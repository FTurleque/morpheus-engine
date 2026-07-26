package com.morpheus.application.lifecycle.mutation;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Explicit caller intent to mutate operational lifecycle state. */
public record ChangeLifecycleMutationCommand(
        ChangeLifecycleMutationId mutationId,
        ChangeLifecycleIdempotencyKey idempotencyKey,
        ProjectSpecificationId projectId,
        ChangeId changeId,
        ChangeLifecycleRevision expectedRevision,
        ChangeLifecycleState targetState,
        Optional<ChangeAbandonmentReason> targetAbandonmentReason,
        boolean confirmed,
        String actor,
        Instant requestedAt) {

    public ChangeLifecycleMutationCommand {
        Objects.requireNonNull(mutationId, "mutationId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        Objects.requireNonNull(targetState, "targetState");
        targetAbandonmentReason = Objects.requireNonNull(targetAbandonmentReason, "targetAbandonmentReason");
        actor = requireNonBlank(actor, "actor");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (targetState != ChangeLifecycleState.ABANDONED && targetAbandonmentReason.isPresent()) {
            throw new IllegalArgumentException("abandonment reason is only valid when targetState is ABANDONED");
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
