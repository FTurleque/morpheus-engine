package com.morpheus.domain.change.lifecycle;

/** Structured reason explaining why a change proposal was abandoned. */
public enum ChangeAbandonmentReason {
    REJECTED,
    OBSOLETE,
    DUPLICATE,
    NOT_FEASIBLE,
    NO_LONGER_NEEDED,
    SUPERSEDED_BY_OTHER_CHANGE,
    UNKNOWN
}
