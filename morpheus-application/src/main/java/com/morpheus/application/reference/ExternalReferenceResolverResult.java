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
        return empty(Status.NOT_FOUND);
    }

    public static ExternalReferenceResolverResult unavailable() {
        return empty(Status.UNAVAILABLE);
    }

    public static ExternalReferenceResolverResult ambiguous() {
        return empty(Status.AMBIGUOUS);
    }

    public static ExternalReferenceResolverResult revisionMismatch() {
        return empty(Status.REVISION_MISMATCH);
    }

    public static ExternalReferenceResolverResult unsupported() {
        return empty(Status.UNSUPPORTED);
    }

    private static ExternalReferenceResolverResult empty(Status status) {
        return new ExternalReferenceResolverResult(status, Optional.empty());
    }

    public enum Status {
        FOUND,
        NOT_FOUND,
        UNAVAILABLE,
        AMBIGUOUS,
        REVISION_MISMATCH,
        UNSUPPORTED
    }
}
