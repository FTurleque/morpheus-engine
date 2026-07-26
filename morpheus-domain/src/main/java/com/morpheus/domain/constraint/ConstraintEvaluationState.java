package com.morpheus.domain.constraint;

/** Deterministic result of evaluating one constraint for one lifecycle target. */
public enum ConstraintEvaluationState {
    NOT_APPLICABLE,
    NON_BLOCKING,
    BLOCKING,
    UNKNOWN
}
