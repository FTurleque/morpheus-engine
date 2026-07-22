package com.morpheus.domain.provider;

/** Capabilities a specification provider may expose for a concrete source. */
public enum ProviderCapability {
    DISCOVER_PROJECT,
    READ_CURRENT_SPECIFICATIONS,
    READ_CHANGES,
    READ_REQUIREMENTS,
    READ_CONSTRAINTS,
    READ_SCENARIOS,
    READ_DESIGN_DECISIONS,
    READ_ACCEPTANCE_CRITERIA,
    READ_IMPLEMENTATION_TASKS,
    READ_HISTORY,
    READ_ARCHIVES,
    INCREMENTAL_READ,
    WATCH_CHANGES,
    WRITE_CHANGE,
    WRITE_TASK_STATE,
    ARCHIVE_CHANGE
}
