package com.morpheus.application.quality;

/** Whether MORPHEUS can evaluate acceptance-criterion coverage for the addressed knowledge projection. */
public enum AcceptanceCoverageStatus {
    EVALUATED,
    NO_CRITERIA,
    UNAVAILABLE_IN_PROPOSED_CHANGE_SET,
    UNAVAILABLE_IN_NORMALIZED_MODEL
}
