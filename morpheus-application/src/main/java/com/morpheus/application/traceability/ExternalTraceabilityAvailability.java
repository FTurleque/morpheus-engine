package com.morpheus.application.traceability;

/** Observable availability of the external reference behind a persisted traceability link. */
public enum ExternalTraceabilityAvailability {
    REFERENCE_UNVALIDATED,
    REFERENCE_UNRESOLVED,
    REFERENCE_RESOLVED,
    REFERENCE_STALE,
    BROKEN_REFERENCE
}
