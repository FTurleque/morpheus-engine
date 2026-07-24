package com.morpheus.application.context;

import com.morpheus.application.reference.ExternalIntegrationStatus;

import java.util.Objects;
import java.util.Optional;

/** Live external-context observation; absence of a bundle is an explicit availability state, not invented context. */
public record TechnicalContextObservation(
        ExternalIntegrationStatus status,
        Optional<TechnicalContextBundle> bundle) {

    public TechnicalContextObservation {
        Objects.requireNonNull(status, "status");
        bundle = Objects.requireNonNull(bundle, "bundle");
        if (bundle.isPresent() && !"AVAILABLE".equals(status.state())) {
            throw new IllegalArgumentException("a technical context bundle requires AVAILABLE integration status");
        }
    }

    public static TechnicalContextObservation unavailable(ExternalIntegrationStatus status) {
        return new TechnicalContextObservation(status, Optional.empty());
    }

    public static TechnicalContextObservation available(
            ExternalIntegrationStatus status,
            TechnicalContextBundle bundle) {
        return new TechnicalContextObservation(status, Optional.of(Objects.requireNonNull(bundle, "bundle")));
    }
}
