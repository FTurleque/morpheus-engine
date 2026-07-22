package com.morpheus.domain.source;

import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;
import java.util.Optional;

/** Immutable descriptor of a specification source discovered by a provider. */
public record SpecificationSource(
        ProviderId providerId,
        SourceLocator locator,
        Optional<String> schema,
        Optional<String> formatVersion,
        ProviderCapabilitySet capabilities) implements Comparable<SpecificationSource> {

    public SpecificationSource {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(locator, "locator");
        schema = Objects.requireNonNull(schema, "schema");
        formatVersion = Objects.requireNonNull(formatVersion, "formatVersion");
        Objects.requireNonNull(capabilities, "capabilities");
    }

    @Override
    public int compareTo(SpecificationSource other) {
        int providerComparison = providerId.compareTo(other.providerId);
        return providerComparison != 0 ? providerComparison : locator.compareTo(other.locator);
    }
}
