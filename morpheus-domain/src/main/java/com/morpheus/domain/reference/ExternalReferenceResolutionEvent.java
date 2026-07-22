package com.morpheus.domain.reference;

import java.time.Instant;
import java.util.Objects;

/** Auditable transition in the resolution history of an external reference. */
public record ExternalReferenceResolutionEvent(
        ExternalReferenceResolutionState previousState,
        ExternalReferenceResolutionState newState,
        ExternalReferenceResolutionReason reason,
        Instant occurredAt) {

    public ExternalReferenceResolutionEvent {
        Objects.requireNonNull(previousState, "previousState");
        Objects.requireNonNull(newState, "newState");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
