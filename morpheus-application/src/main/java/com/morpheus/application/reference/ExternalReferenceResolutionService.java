package com.morpheus.application.reference;

import com.morpheus.application.operability.OperationalExecution;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;

import java.time.Clock;
import java.util.Objects;

/** Resolves external references without making any target system mandatory. */
public final class ExternalReferenceResolutionService {
    private final ExternalReferenceResolverRegistry registry;
    private final Clock clock;
    private final OperationalExecution execution;

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
            return reference.withoutResolver(clock.instant());
        }

        ExternalReferenceResolverResult result = execution.externalCall(
                reference.target().system(),
                "resolve",
                () -> resolver.orElseThrow().resolve(reference.target()));
        return switch (result.status()) {
            case FOUND -> reference.resolvedAs(result.resolvedTarget().orElseThrow(), clock.instant());
            case NOT_FOUND -> reference.notFound(
                    ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                    ExternalReferenceResolutionReason.TARGET_REMOVED,
                    clock.instant());
            case UNAVAILABLE -> reference.notFound(
                    ExternalReferenceResolutionReason.TARGET_UNAVAILABLE,
                    ExternalReferenceResolutionReason.TARGET_UNAVAILABLE,
                    clock.instant());
            case AMBIGUOUS -> reference.notFound(
                    ExternalReferenceResolutionReason.TARGET_AMBIGUOUS,
                    ExternalReferenceResolutionReason.TARGET_AMBIGUOUS,
                    clock.instant());
            case REVISION_MISMATCH -> reference.notFound(
                    ExternalReferenceResolutionReason.TARGET_REVISION_MISMATCH,
                    ExternalReferenceResolutionReason.TARGET_REVISION_MISMATCH,
                    clock.instant());
            case UNSUPPORTED -> reference.notFound(
                    ExternalReferenceResolutionReason.TARGET_TYPE_UNSUPPORTED,
                    ExternalReferenceResolutionReason.TARGET_TYPE_UNSUPPORTED,
                    clock.instant());
        };
    }
}
