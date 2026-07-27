package com.morpheus.application.reference;

import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.operability.OperationalExecution;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Resolves external references without making any target system mandatory. */
public final class ExternalReferenceResolutionService {
    private final ExternalReferenceResolverRegistry registry;
    private final Clock clock;
    private final OperationalExecution execution;

    public ExternalReferenceResolutionService(ExternalReferenceResolverRegistry registry, Clock clock) {
        this(registry, clock, new OperationalExecution(LocalOperationalRuntime.recorder()));
    }

    public ExternalReferenceResolutionService(
            ExternalReferenceResolverRegistry registry,
            Clock clock,
            OperationalExecution execution) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    public ExternalReference resolve(ExternalReference reference) {
        Objects.requireNonNull(reference, "reference");
        var resolver = registry.find(reference.target().system());
        if (resolver.isEmpty()) {
            return reference.transition(
                    ExternalReferenceResolutionState.UNRESOLVED,
                    ExternalReferenceResolutionReason.NO_RESOLVER,
                    Optional.empty(),
                    clock.instant());
        }

        ExternalReferenceResolverResult result = execution.externalCall(
                reference.target().system(),
                "resolve",
                () -> resolver.orElseThrow().resolve(reference.target()));
        return switch (result.status()) {
            case FOUND -> reference.transition(
                    ExternalReferenceResolutionState.RESOLVED,
                    ExternalReferenceResolutionReason.RESOLVED,
                    result.resolvedTarget(),
                    clock.instant());
            case NOT_FOUND -> transitionUnresolvedOrStale(
                    reference,
                    ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                    ExternalReferenceResolutionReason.TARGET_REMOVED);
            case UNAVAILABLE -> transitionUnresolvedOrStale(
                    reference,
                    ExternalReferenceResolutionReason.TARGET_UNAVAILABLE,
                    ExternalReferenceResolutionReason.TARGET_UNAVAILABLE);
            case AMBIGUOUS -> transitionUnresolvedOrStale(
                    reference,
                    ExternalReferenceResolutionReason.TARGET_AMBIGUOUS,
                    ExternalReferenceResolutionReason.TARGET_AMBIGUOUS);
            case REVISION_MISMATCH -> transitionUnresolvedOrStale(
                    reference,
                    ExternalReferenceResolutionReason.TARGET_REVISION_MISMATCH,
                    ExternalReferenceResolutionReason.TARGET_REVISION_MISMATCH);
            case UNSUPPORTED -> transitionUnresolvedOrStale(
                    reference,
                    ExternalReferenceResolutionReason.TARGET_TYPE_UNSUPPORTED,
                    ExternalReferenceResolutionReason.TARGET_TYPE_UNSUPPORTED);
        };
    }

    private ExternalReference transitionUnresolvedOrStale(
            ExternalReference reference,
            ExternalReferenceResolutionReason unresolvedReason,
            ExternalReferenceResolutionReason staleReason) {
        boolean previouslyResolved = wasPreviouslyResolved(reference);
        return reference.transition(
                previouslyResolved ? ExternalReferenceResolutionState.STALE : ExternalReferenceResolutionState.UNRESOLVED,
                previouslyResolved ? staleReason : unresolvedReason,
                Optional.empty(),
                clock.instant());
    }

    private boolean wasPreviouslyResolved(ExternalReference reference) {
        return reference.resolutionState() == ExternalReferenceResolutionState.RESOLVED
                || reference.resolutionState() == ExternalReferenceResolutionState.STALE
                || reference.history().stream()
                .anyMatch(event -> event.newState() == ExternalReferenceResolutionState.RESOLVED);
    }
}
