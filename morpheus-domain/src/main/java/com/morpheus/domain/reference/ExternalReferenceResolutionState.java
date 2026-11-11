package com.morpheus.domain.reference;

import com.morpheus.domain.traceability.TraceabilityResolutionState;

public enum ExternalReferenceResolutionState {
    UNVALIDATED,
    UNRESOLVED,
    RESOLVED,
    STALE;

    /**
     * The resolution a traceability link records when it observes a reference in this state: a stale reference was
     * found once and is only partially resolved, and no state yields a heuristic resolution.
     */
    public TraceabilityResolutionState traceabilityResolution() {
        return switch (this) {
            case UNVALIDATED, UNRESOLVED -> TraceabilityResolutionState.UNRESOLVED;
            case RESOLVED -> TraceabilityResolutionState.RESOLVED;
            case STALE -> TraceabilityResolutionState.PARTIALLY_RESOLVED;
        };
    }
}
