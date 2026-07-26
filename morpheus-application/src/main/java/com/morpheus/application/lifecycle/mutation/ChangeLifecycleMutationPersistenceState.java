package com.morpheus.application.lifecycle.mutation;

/** Store-level result restricted to atomic persistence concerns. */
public enum ChangeLifecycleMutationPersistenceState {
    APPLIED,
    ALREADY_APPLIED,
    CONFLICT
}
