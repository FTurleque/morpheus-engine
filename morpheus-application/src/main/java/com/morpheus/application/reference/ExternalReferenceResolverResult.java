package com.morpheus.application.reference;

import com.morpheus.domain.reference.ResolvedExternalTarget;

import java.util.Objects;
import java.util.Optional;

public record ExternalReferenceResolverResult(
        Status status,
        Optional<ResolvedExternalTarget> resolvedTarget) {

    public ExternalReferenceResolverResult {
        Objects.requireNonNull(status, "status");
        resolvedTarget = Objects.requireNonNull(resolvedTarget, "resolvedTarget");
        if (status == Status.FOUND && resolvedTarget.isEmpty()) {
            throw new IllegalArgumentException("FOUND result requires a resolved target");
        }
        if (status != Status.FOUND && resolvedTarget.isPresent()) {
            throw new IllegalArgumentException("only FOUND may contain a resolved target");
        }
    }

    public static ExternalReferenceResolverResult found(ResolvedExternalTarget target) {
        return new ExternalReferenceResolverResult(Status.FOUND, Optional.of(target));
    }

    public static ExternalReferenceResolverResult notFound() {
        return new ExternalReferenceResolverResult(Status.NOT_FOUND, Optional.empty());
    }

    public static ExternalReferenceResolverResult unavailable() {
        return new ExternalReferenceResolverResult(Status.UNAVAILABLE, Optional.empty());
    }

    public enum Status {
        FOUND,
        NOT_FOUND,
        UNAVAILABLE
    }
}
