package com.morpheus.application.analysis;

/** Machine-readable warnings emitted when change impact cannot be fully demonstrated from normalized facts. */
public enum ChangeAnalysisWarningCode {
    ADDED_REQUIREMENT_ALREADY_CURRENT,
    MODIFIED_REQUIREMENT_BASELINE_MISSING,
    REMOVED_REQUIREMENT_BASELINE_MISSING,
    SPECIFICATION_KEY_UNRESOLVED,
    MODIFIED_WITHOUT_DOCUMENTARY_CHANGE,
    TRACEABILITY_PATH_PARTIALLY_RESOLVED,
    ACCEPTANCE_CRITERIA_UNAVAILABLE
}
