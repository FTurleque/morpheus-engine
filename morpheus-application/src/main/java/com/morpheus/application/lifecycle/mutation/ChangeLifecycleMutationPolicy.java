package com.morpheus.application.lifecycle.mutation;

/** Policy controlling side-effect confirmation without weakening lifecycle business rules. */
public record ChangeLifecycleMutationPolicy(boolean confirmationRequired) {
    public static ChangeLifecycleMutationPolicy strict() {
        return new ChangeLifecycleMutationPolicy(true);
    }

    public static ChangeLifecycleMutationPolicy trustedAutomation() {
        return new ChangeLifecycleMutationPolicy(false);
    }
}
