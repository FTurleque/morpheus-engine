package com.morpheus.application.composition;

import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;

/** Persistable summary of one provider participation in a composition run. */
public record CompositionProviderState(
        ProviderId providerId,
        int priority,
        boolean required,
        boolean available,
        int diagnosticCount) {

    public CompositionProviderState {
        Objects.requireNonNull(providerId, "providerId");
        if (diagnosticCount < 0) {
            throw new IllegalArgumentException("diagnosticCount must be non-negative");
        }
    }

    public static CompositionProviderState from(ProviderContribution contribution) {
        Objects.requireNonNull(contribution, "contribution");
        return new CompositionProviderState(
                contribution.providerId(),
                contribution.priority(),
                contribution.required(),
                contribution.available(),
                contribution.readResult().diagnostics().size());
    }
}
