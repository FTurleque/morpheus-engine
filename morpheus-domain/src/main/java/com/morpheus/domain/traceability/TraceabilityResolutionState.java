package com.morpheus.domain.traceability;

/** Resolution quality of one traceability observation, independent from its semantic relation type. */
public enum TraceabilityResolutionState {
    RESOLVED,
    PARTIALLY_RESOLVED,
    UNRESOLVED,
    HEURISTIC
}
