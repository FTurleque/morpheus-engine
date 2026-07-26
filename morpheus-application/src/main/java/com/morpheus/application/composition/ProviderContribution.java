package com.morpheus.application.composition;

import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;
import java.util.Optional;

/** Summary of one provider's participation in a composed read. */
public record ProviderContribution(
        ProviderId providerId,
        int precedence,
        boolean required,
        ProviderContributionStatus status,
        int itemCount,
        Optional<String> detail) {

    public ProviderContribution {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(status, "status");
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount must be >= 0");
        }
        detail = Objects.requireNonNull(detail, "detail")
                .map(String::trim)
                .filter(value -> !value.isEmpty());
    }
}
