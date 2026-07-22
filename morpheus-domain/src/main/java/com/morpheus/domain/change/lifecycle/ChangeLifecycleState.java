package com.morpheus.domain.change.lifecycle;

/** Canonical MORPHEUS business lifecycle of a change proposal. */
public enum ChangeLifecycleState {
    DRAFT,
    PROPOSED,
    SPECIFIED,
    DESIGNED,
    PLANNED,
    IMPLEMENTING,
    VERIFYING,
    COMPLETED,
    ARCHIVED,
    ABANDONED
}
