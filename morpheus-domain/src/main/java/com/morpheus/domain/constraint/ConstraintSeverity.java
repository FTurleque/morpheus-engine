package com.morpheus.domain.constraint;

/** Provider-neutral importance of a constraint observation. Severity never implies blocking by itself. */
public enum ConstraintSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL,
    UNKNOWN
}
