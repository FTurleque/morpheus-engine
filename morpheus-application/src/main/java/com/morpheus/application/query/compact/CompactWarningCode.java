package com.morpheus.application.query.compact;

/** Stable machine-readable warnings emitted by compact M5 query views. */
public enum CompactWarningCode {
    CHANGE_NOT_FOUND,
    AFFECTED_REQUIREMENT_UNRESOLVED,
    EXTERNAL_REFERENCE_UNVALIDATED,
    EXTERNAL_REFERENCE_UNRESOLVED,
    EXTERNAL_REFERENCE_STALE,
    EXTERNAL_REFERENCE_BROKEN,
    EVIDENCE_NOT_FOUND
}
