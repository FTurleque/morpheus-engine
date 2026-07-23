package com.morpheus.domain.traceability;

/** Whether a relation may be traversed compositionally without asserting an implicit new business edge. */
public enum TraceabilityTransitivityPolicy {
    NON_TRANSITIVE,
    CONTEXTUAL
}
