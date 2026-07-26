package com.morpheus.application.composition;

/** Outcome of one provider contribution during a multi-provider read. */
public enum ProviderContributionStatus {
    READ,
    PARTIAL,
    ABSENT,
    UNSUPPORTED,
    FAILED
}
