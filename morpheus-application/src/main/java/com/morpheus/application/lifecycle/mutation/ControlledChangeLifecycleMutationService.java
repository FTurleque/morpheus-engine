package com.morpheus.application.lifecycle.mutation;

import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationState;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** Explicit side-effect boundary for controlled operational lifecycle transitions. */
public final class ControlledChangeLifecycleMutationService {
    private final ChangeTransitionEvaluationService transitionEvaluation;
    private final ChangeLifecycleMutationStore store;
    private final ChangeWriteCapabilityResolver writeCapability;
    private final ChangeLifecyclePolicy lifecyclePolicy;
    private final Clock clock;

    public ControlledChangeLifecycleMutationService(
            ChangeTransitionEvaluationService transitionEvaluation,
            ChangeLifecycleMutationStore store,
            ChangeWriteCapabilityResolver writeCapability) {
        this(transitionEvaluation, store, writeCapability, ChangeLifecyclePolicy.forwardOnly(), Clock.systemUTC());
    }

    public ControlledChangeLifecycleMutationService(
            ChangeTransitionEvaluationService transitionEvaluation,
            ChangeLifecycleMutationStore store,
            ChangeWriteCapabilityResolver writeCapability,
            ChangeLifecyclePolicy lifecyclePolicy,
            Clock clock) {
        this.transitionEvaluation = Objects.requireNonNull(transitionEvaluation, "transitionEvaluation");
        this.store = Objects.requireNonNull(store, "store");
        this.writeCapability = Objects.requireNonNull(writeCapability, "writeCapability");
        this.lifecyclePolicy = Objects.requireNonNull(lifecyclePolicy, "lifecyclePolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ChangeLifecycleMutationResult apply(
            ChangeLifecycleMutationCommand command,
            ChangeLifecycleMutationPolicy mutationPolicy) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(mutationPolicy, "mutationPolicy");

        String fingerprint = fingerprint(command);
        Optional<ChangeLifecycleMutationAuditRecord> previous =
                store.findByIdempotencyKey(command.projectId(), command.idempotencyKey());
        if (previous.isPresent()) {
            ChangeLifecycleMutationAuditRecord audit = previous.orElseThrow();
            if (!audit.commandFingerprint().equals(fingerprint)) {
                return ChangeLifecycleMutationResult.simple(
                        ChangeLifecycleMutationResultState.CONFLICT,
                        "Idempotency key was already used for a different lifecycle mutation command");
            }
            return alreadyApplied(audit);
        }

        ChangeWriteCapabilityObservation authorization = writeCapability.resolve(command.projectId());
        if (!authorization.writeAllowed()) {
            return ChangeLifecycleMutationResult.simple(
                    ChangeLifecycleMutationResultState.NOT_AUTHORIZED,
                    authorization.reason());
        }

        if (mutationPolicy.confirmationRequired() && !command.confirmed()) {
            return ChangeLifecycleMutationResult.simple(
                    ChangeLifecycleMutationResultState.REQUIRES_CONFIRMATION,
                    "Lifecycle mutation requires explicit confirmation");
        }

        ChangeLifecycleOperationalState current = store.findState(command.projectId(), command.changeId())
                .orElseGet(() -> ChangeLifecycleOperationalState.initial(command.projectId(), command.changeId()));
        if (!current.revision().equals(command.expectedRevision())) {
            return ChangeLifecycleMutationResult.simple(
                    ChangeLifecycleMutationResultState.CONFLICT,
                    "Expected lifecycle revision " + command.expectedRevision()
                            + " does not match current revision " + current.revision());
        }

        var evaluation = transitionEvaluation.evaluateActive(
                        command.projectId(),
                        current.lifecycle(),
                        command.targetState(),
                        lifecyclePolicy,
                        command.targetAbandonmentReason())
                .orElse(null);
        if (evaluation == null) {
            return ChangeLifecycleMutationResult.simple(
                    ChangeLifecycleMutationResultState.REJECTED,
                    "No ACTIVE snapshot is available for lifecycle evaluation");
        }
        if (evaluation.state() != ChangeTransitionEvaluationState.ALLOWED) {
            return ChangeLifecycleMutationResult.simple(
                    ChangeLifecycleMutationResultState.REJECTED,
                    "Lifecycle transition is not eligible for mutation: " + evaluation.state() + " - " + evaluation.reason());
        }

        Instant appliedAt = clock.instant();
        ChangeLifecycleMutationAttempt attempt = new ChangeLifecycleMutationAttempt(
                command.mutationId(),
                command.idempotencyKey(),
                fingerprint,
                command.projectId(),
                command.changeId(),
                current.lifecycle().state(),
                command.targetState(),
                command.targetAbandonmentReason(),
                command.expectedRevision(),
                command.actor(),
                authorization.providerId().orElseThrow(),
                evaluation.reason(),
                appliedAt);

        ChangeLifecycleMutationPersistenceResult persisted = store.apply(attempt);
        return switch (persisted.state()) {
            case APPLIED -> new ChangeLifecycleMutationResult(
                    ChangeLifecycleMutationResultState.APPLIED,
                    persisted.lifecycleState(),
                    persisted.audit(),
                    persisted.reason());
            case ALREADY_APPLIED -> new ChangeLifecycleMutationResult(
                    ChangeLifecycleMutationResultState.ALREADY_APPLIED,
                    persisted.lifecycleState(),
                    persisted.audit(),
                    persisted.reason());
            case CONFLICT -> new ChangeLifecycleMutationResult(
                    ChangeLifecycleMutationResultState.CONFLICT,
                    persisted.lifecycleState(),
                    Optional.empty(),
                    persisted.reason());
        };
    }

    private ChangeLifecycleMutationResult alreadyApplied(ChangeLifecycleMutationAuditRecord audit) {
        ChangeLifecycle lifecycle = audit.targetState() == ChangeLifecycleState.ABANDONED
                ? ChangeLifecycle.abandoned(audit.changeId(), audit.targetAbandonmentReason().orElseThrow())
                : ChangeLifecycle.of(audit.changeId(), audit.targetState());
        ChangeLifecycleOperationalState state = new ChangeLifecycleOperationalState(
                audit.projectId(),
                lifecycle,
                audit.toRevision(),
                Optional.of(audit.appliedAt()),
                Optional.of(audit.mutationId()));
        return new ChangeLifecycleMutationResult(
                ChangeLifecycleMutationResultState.ALREADY_APPLIED,
                Optional.of(state),
                Optional.of(audit),
                "Idempotent lifecycle mutation was already applied");
    }

    private String fingerprint(ChangeLifecycleMutationCommand command) {
        String canonical = String.join("|",
                command.projectId().toString(),
                command.changeId().toString(),
                command.expectedRevision().toString(),
                command.targetState().name(),
                command.targetAbandonmentReason().map(Enum::name).orElse(""),
                command.actor());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
