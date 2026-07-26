package com.morpheus.application.composition;

import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;

/** Explicit provider participation policy for one composition run. */
public record ProviderCompositionSource(ProviderId providerId, int priority, boolean required) {
    public ProviderCompositionSource {
        Objects.requireNonNull(providerId, "providerId");
    }
}
