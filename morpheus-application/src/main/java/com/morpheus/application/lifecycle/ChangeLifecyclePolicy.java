package com.morpheus.application.lifecycle;

/** Explicit policy for lifecycle revisions; no backward transition is implicit. */
public record ChangeLifecyclePolicy(
        boolean allowBackwardTransitions,
        boolean allowCompletedReopen) {

    public static ChangeLifecyclePolicy forwardOnly() {
        return new ChangeLifecyclePolicy(false, false);
    }

    public static ChangeLifecyclePolicy revisionsAllowed() {
        return new ChangeLifecyclePolicy(true, false);
    }

    public static ChangeLifecyclePolicy revisionsAndCompletedReopenAllowed() {
        return new ChangeLifecyclePolicy(true, true);
    }
}
