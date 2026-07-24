package com.morpheus.api;

/** Strict M14 HTTP payload for a read-only lifecycle transition evaluation. */
public record TransitionCheckRequest(
        String fromState,
        String fromAbandonmentReason,
        String targetState,
        String abandonmentReason,
        Boolean allowBackwardTransitions,
        Boolean allowCompletedReopen) {
}
