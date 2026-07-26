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

/** Immutable append-only evidence of one applied lifecycle mutation. */
public record ChangeLifecycleMutationAuditRecord(
        ChangeLifecycleMutationId mutationId,
        ChangeLifecycleIdempotencyKey idempotencyKey,
        String commandFingerprint,
        ProjectSpecificationId projectId,
        ChangeId changeId,
        ChangeLifecycleState fromState,
        ChangeLifecycleState targetState,
        Optional<ChangeAbandonmentReason> targetAbandonmentReason,
        ChangeLifecycleRevision fromRevision,
        ChangeLifecycleRevision toRevision,
        String actor,
        ProviderId providerId,
        String reason,
        Instant appliedAt) {

    public ChangeLifecycleMutationAuditRecord {
        Objects.requireNonNull(mutationId, "mutationId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        commandFingerprint = requireNonBlank(commandFingerprint, "commandFingerprint");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(fromState, "fromState");
        Objects.requireNonNull(targetState, "targetState");
        targetAbandonmentReason = Objects.requireNonNull(targetAbandonmentReason, "targetAbandonmentReason");
        Objects.requireNonNull(fromRevision, "fromRevision");
        Objects.requireNonNull(toRevision, "toRevision");
        actor = requireNonBlank(actor, "actor");
        Objects.requireNonNull(providerId, "providerId");
        reason = requireNonBlank(reason, "reason");
        Objects.requireNonNull(appliedAt, "appliedAt");
        if (!toRevision.equals(fromRevision.next())) {
            throw new IllegalArgumentException("audit toRevision must equal fromRevision + 1");
        }
        if (targetState == ChangeLifecycleState.ABANDONED && targetAbandonmentReason.isEmpty()) {
            throw new IllegalArgumentException("ABANDONED audit record requires an abandonment reason");
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
