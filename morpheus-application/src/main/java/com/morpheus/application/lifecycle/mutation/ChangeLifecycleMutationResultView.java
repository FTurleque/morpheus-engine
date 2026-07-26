package com.morpheus.application.lifecycle.mutation;

import java.util.Objects;
import java.util.Optional;

/** JSON-safe application projection shared by CLI, MCP and HTTP write surfaces. */
public record ChangeLifecycleMutationResultView(
        String state,
        Optional<LifecycleStateView> lifecycleState,
        Optional<AuditView> audit,
        String reason) {

    public ChangeLifecycleMutationResultView {
        state = requireNonBlank(state, "state");
        lifecycleState = Objects.requireNonNull(lifecycleState, "lifecycleState");
        audit = Objects.requireNonNull(audit, "audit");
        reason = requireNonBlank(reason, "reason");
    }

    public static ChangeLifecycleMutationResultView from(ChangeLifecycleMutationResult result) {
        Objects.requireNonNull(result, "result");
        return new ChangeLifecycleMutationResultView(
                result.state().name(),
                result.lifecycleState().map(LifecycleStateView::from),
                result.audit().map(AuditView::from),
                result.reason());
    }

    public record LifecycleStateView(
            String projectId,
            String changeId,
            String lifecycleState,
            Optional<String> abandonmentReason,
            long revision,
            Optional<String> updatedAt,
            Optional<String> lastMutationId) {
        static LifecycleStateView from(ChangeLifecycleOperationalState state) {
            return new LifecycleStateView(
                    state.projectId().toString(),
                    state.lifecycle().changeId().toString(),
                    state.lifecycle().state().name(),
                    state.lifecycle().abandonmentReason().map(Enum::name),
                    state.revision().value(),
                    state.updatedAt().map(Object::toString),
                    state.lastMutationId().map(Object::toString));
        }
    }

    public record AuditView(
            String mutationId,
            String idempotencyKey,
            String projectId,
            String changeId,
            String fromState,
            String targetState,
            Optional<String> targetAbandonmentReason,
            long fromRevision,
            long toRevision,
            String actor,
            String providerId,
            String reason,
            String appliedAt) {
        static AuditView from(ChangeLifecycleMutationAuditRecord audit) {
            return new AuditView(
                    audit.mutationId().toString(),
                    audit.idempotencyKey().toString(),
                    audit.projectId().toString(),
                    audit.changeId().toString(),
                    audit.fromState().name(),
                    audit.targetState().name(),
                    audit.targetAbandonmentReason().map(Enum::name),
                    audit.fromRevision().value(),
                    audit.toRevision().value(),
                    audit.actor(),
                    audit.providerId().toString(),
                    audit.reason(),
                    audit.appliedAt().toString());
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
