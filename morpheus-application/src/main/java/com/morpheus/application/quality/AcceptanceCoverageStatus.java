package com.morpheus.application.quality;

/** Whether MORPHEUS can evaluate normalized acceptance-criterion coverage for a published snapshot. */
public enum AcceptanceCoverageStatus {
    EVALUATED,
    NO_CRITERIA,
    UNAVAILABLE_IN_NORMALIZED_MODEL
}
