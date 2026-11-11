package com.morpheus.domain.reference;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provenance.Provenance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** MORPHEUS-owned link to a resource in an optional external system. */
public record ExternalReference(
        ExternalReferenceId id,
        DomainIdentity ownerId,
        ExternalReferenceTarget target,
        ExternalReferenceResolutionState resolutionState,
        ExternalReferenceResolutionReason resolutionReason,
        Optional<ResolvedExternalTarget> resolvedTarget,
        Optional<Provenance> provenance,
        List<ExternalReferenceResolutionEvent> history) {

    public ExternalReference {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(resolutionState, "resolutionState");
        Objects.requireNonNull(resolutionReason, "resolutionReason");
        resolvedTarget = Objects.requireNonNull(resolvedTarget, "resolvedTarget");
        provenance = Objects.requireNonNull(provenance, "provenance");
        history = List.copyOf(Objects.requireNonNull(history, "history"));

        if (resolutionState == ExternalReferenceResolutionState.RESOLVED && resolvedTarget.isEmpty()) {
            throw new IllegalArgumentException("RESOLVED reference must contain a resolved target");
        }
        if (resolutionState != ExternalReferenceResolutionState.RESOLVED && resolvedTarget.isPresent()) {
            throw new IllegalArgumentException("only RESOLVED reference may contain a resolved target");
        }
    }

    public static ExternalReference unvalidated(
            ExternalReferenceId id,
            DomainIdentity ownerId,
            ExternalReferenceTarget target,
            Optional<Provenance> provenance) {
        return new ExternalReference(
                id,
                ownerId,
                target,
                ExternalReferenceResolutionState.UNVALIDATED,
                ExternalReferenceResolutionReason.NOT_ATTEMPTED,
                Optional.empty(),
                provenance,
                List.of());
    }

    public boolean isResolved() {
        return resolutionState == ExternalReferenceResolutionState.RESOLVED;
    }

    /** Whether the target was ever found: the reference is resolved or stale now, or was resolved once. */
    public boolean hasBeenResolved() {
        return resolutionState == ExternalReferenceResolutionState.RESOLVED
                || resolutionState == ExternalReferenceResolutionState.STALE
                || history.stream().anyMatch(event -> event.newState() == ExternalReferenceResolutionState.RESOLVED);
    }

    public ExternalReference resolvedAs(ResolvedExternalTarget resolved, Instant occurredAt) {
        return transition(
                ExternalReferenceResolutionState.RESOLVED,
                ExternalReferenceResolutionReason.RESOLVED,
                Optional.of(Objects.requireNonNull(resolved, "resolved")),
                occurredAt);
    }

    /** No resolver knows the target system: the reference stays unresolved, never stale. */
    public ExternalReference withoutResolver(Instant occurredAt) {
        return transition(
                ExternalReferenceResolutionState.UNRESOLVED,
                ExternalReferenceResolutionReason.NO_RESOLVER,
                Optional.empty(),
                occurredAt);
    }

    /**
     * The resolver did not find the target: a reference that was ever resolved becomes {@code STALE} with the stale
     * reason, any other becomes {@code UNRESOLVED} with the unresolved reason.
     */
    public ExternalReference notFound(
            ExternalReferenceResolutionReason unresolvedReason,
            ExternalReferenceResolutionReason staleReason,
            Instant occurredAt) {
        boolean resolvedOnce = hasBeenResolved();
        return transition(
                resolvedOnce ? ExternalReferenceResolutionState.STALE : ExternalReferenceResolutionState.UNRESOLVED,
                Objects.requireNonNull(resolvedOnce ? staleReason : unresolvedReason, "reason"),
                Optional.empty(),
                occurredAt);
    }

    public ExternalReference transition(
            ExternalReferenceResolutionState newState,
            ExternalReferenceResolutionReason reason,
            Optional<ResolvedExternalTarget> newResolvedTarget,
            Instant occurredAt) {
        Objects.requireNonNull(newState, "newState");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(newResolvedTarget, "newResolvedTarget");
        Objects.requireNonNull(occurredAt, "occurredAt");

        List<ExternalReferenceResolutionEvent> nextHistory = new ArrayList<>(history);
        nextHistory.add(new ExternalReferenceResolutionEvent(resolutionState, newState, reason, occurredAt));
        return new ExternalReference(
                id,
                ownerId,
                target,
                newState,
                reason,
                newResolvedTarget,
                provenance,
                nextHistory);
    }
}
