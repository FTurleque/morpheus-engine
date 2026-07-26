package com.morpheus.application.lifecycle.mutation;

/** Stable outcome taxonomy for one controlled lifecycle mutation request. */
public enum ChangeLifecycleMutationResultState {
    APPLIED,
    ALREADY_APPLIED,
    CONFLICT,
    NOT_AUTHORIZED,
    REQUIRES_CONFIRMATION,
    REJECTED
}
