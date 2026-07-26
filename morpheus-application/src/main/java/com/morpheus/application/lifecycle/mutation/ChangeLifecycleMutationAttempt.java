package com.morpheus.application.lifecycle.mutation;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Fully validated persistence attempt; stores still enforce CAS and idempotency atomically. */
public record ChangeLifecycleMutationAttempt(
        ChangeLifecycleMutationId mutationId,
        ChangeLifecycleIdempotencyKey idempotencyKey,
        String commandFingerprint,
        ProjectSpecificationId projectId,
        ChangeId changeId,
        ChangeLifecycleState fromState,
        ChangeLifecycleState targetState,
        Optional<ChangeAbandonmentReason> targetAbandonmentReason,
        ChangeLifecycleRevision expectedRevision,
        String actor,
        ProviderId providerId,
        String reason,
        Instant appliedAt) {

    public ChangeLifecycleMutationAttempt {
        Objects.requireNonNull(mutationId, "mutationId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        commandFingerprint = requireNonBlank(commandFingerprint, "commandFingerprint");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(fromState, "fromState");
        Objects.requireNonNull(targetState, "targetState");
        targetAbandonmentReason = Objects.requireNonNull(targetAbandonmentReason, "targetAbandonmentReason");
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        actor = requireNonBlank(actor, "actor");
        Objects.requireNonNull(providerId, "providerId");
        reason = requireNonBlank(reason, "reason");
        Objects.requireNonNull(appliedAt, "appliedAt");

        if (targetState == ChangeLifecycleState.ABANDONED && targetAbandonmentReason.isEmpty()) {
            throw new IllegalArgumentException("ABANDONED persistence attempt requires an abandonment reason");
        }
        if (targetState != ChangeLifecycleState.ABANDONED && targetAbandonmentReason.isPresent()) {
            throw new IllegalArgumentException("abandonment reason is only valid for ABANDONED target state");
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
