package com.morpheus.application.provider;

import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ProviderSelectionRequest(
        Set<ProviderCapability> requiredCapabilities,
        Set<ProviderCapability> preferredCapabilities,
        Optional<ProviderId> explicitProvider,
        boolean allowRemote) {

    public ProviderSelectionRequest {
        requiredCapabilities = Set.copyOf(Objects.requireNonNull(requiredCapabilities, "requiredCapabilities"));
        preferredCapabilities = Set.copyOf(Objects.requireNonNull(preferredCapabilities, "preferredCapabilities"));
        explicitProvider = Objects.requireNonNull(explicitProvider, "explicitProvider");
    }

    public static ProviderSelectionRequest localOnly(
            Set<ProviderCapability> requiredCapabilities,
            Set<ProviderCapability> preferredCapabilities) {
        return new ProviderSelectionRequest(requiredCapabilities, preferredCapabilities, Optional.empty(), false);
    }
}
