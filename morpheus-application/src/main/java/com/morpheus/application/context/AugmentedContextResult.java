package com.morpheus.application.context;

import java.util.Objects;

/** Live composition result: MORPHEUS intent plus an optional external technical-context observation. */
public record AugmentedContextResult(
        AugmentedSnapshotView snapshot,
        MorpheusIntentContext intentContext,
        TechnicalContextObservation technicalContext,
        boolean persisted) {

    public AugmentedContextResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(intentContext, "intentContext");
        Objects.requireNonNull(technicalContext, "technicalContext");
        if (persisted) {
            throw new IllegalArgumentException("M13 augmented context is a live non-persisted observation");
        }
    }
}
