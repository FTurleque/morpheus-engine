package com.morpheus.domain.provider;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable capability set for a provider on a concrete source. */
public record ProviderCapabilitySet(Set<ProviderCapability> values) {

    public ProviderCapabilitySet {
        Objects.requireNonNull(values, "values");
        values = values.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(values));
    }

    public static ProviderCapabilitySet of(ProviderCapability... capabilities) {
        if (capabilities.length == 0) {
            return new ProviderCapabilitySet(Set.of());
        }
        return new ProviderCapabilitySet(EnumSet.of(capabilities[0], capabilities));
    }

    public static ProviderCapabilitySet copyOf(Collection<ProviderCapability> capabilities) {
        return new ProviderCapabilitySet(Set.copyOf(capabilities));
    }

    public boolean contains(ProviderCapability capability) {
        return values.contains(capability);
    }

    public boolean containsAll(Collection<ProviderCapability> required) {
        return values.containsAll(required);
    }

    public long countMatches(Collection<ProviderCapability> requested) {
        return requested.stream().filter(values::contains).count();
    }
}
