package com.morpheus.application.reference;

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

    public ExternalReferenceResolutionService(ExternalReferenceResolverRegistry registry, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
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

        ExternalReferenceResolverResult result = resolver.orElseThrow().resolve(reference.target());
        return switch (result.status()) {
            case FOUND -> reference.transition(
                    ExternalReferenceResolutionState.RESOLVED,
                    ExternalReferenceResolutionReason.RESOLVED,
                    result.resolvedTarget(),
                    clock.instant());
            case NOT_FOUND -> reference.transition(
                    wasPreviouslyResolved(reference)
                            ? ExternalReferenceResolutionState.STALE
                            : ExternalReferenceResolutionState.UNRESOLVED,
                    wasPreviouslyResolved(reference)
                            ? ExternalReferenceResolutionReason.TARGET_REMOVED
                            : ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                    Optional.empty(),
                    clock.instant());
            case UNAVAILABLE -> reference.transition(
                    wasPreviouslyResolved(reference)
                            ? ExternalReferenceResolutionState.STALE
                            : ExternalReferenceResolutionState.UNRESOLVED,
                    ExternalReferenceResolutionReason.TARGET_UNAVAILABLE,
                    Optional.empty(),
                    clock.instant());
        };
    }

    private boolean wasPreviouslyResolved(ExternalReference reference) {
        return reference.resolutionState() == ExternalReferenceResolutionState.RESOLVED
                || reference.resolutionState() == ExternalReferenceResolutionState.STALE
                || reference.history().stream()
                .anyMatch(event -> event.newState() == ExternalReferenceResolutionState.RESOLVED);
    }
}
