package com.morpheus.application.orchestration;

/** Machine-facing result of a read-only lifecycle transition evaluation. */
public enum ChangeTransitionEvaluationState {
    ALLOWED,
    BLOCKED,
    UNKNOWN,
    REQUIRES_INPUT
}
