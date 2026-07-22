package com.morpheus.application.lifecycle;

/** Explicit facts used to validate a change lifecycle transition. */
public record ChangeLifecycleFacts(
        boolean requirementsIdentified,
        boolean criticalConstraintsKnown,
        boolean acceptanceCriteriaDefined,
        boolean designRequired,
        boolean designDecisionsAvailable,
        boolean planPresent,
        boolean knownBlocker,
        boolean blockingAcceptanceCriterionFailed,
        boolean blockingAcceptanceCriterionUnverified) {

    public static ChangeLifecycleFacts permissive() {
        return new ChangeLifecycleFacts(true, true, true, true, true, true, false, false, false);
    }
}
