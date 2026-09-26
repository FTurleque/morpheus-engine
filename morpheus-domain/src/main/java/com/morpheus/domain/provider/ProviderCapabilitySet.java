package com.morpheus.domain.provider;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable capability set for a provider on a concrete source.
 *
 * <p>The set iterates in the declaration order of {@link ProviderCapability}. It used to be frozen by
 * {@code Set.copyOf}, whose iteration order is salted once per JVM, and every rendering of a probe -- CLI text and
 * canonical JSON alike, since the serializer sorts map keys but not collections -- printed the capabilities in an
 * order that changed from one run to the next.</p>
 */
public record ProviderCapabilitySet(Set<ProviderCapability> values) {

    public ProviderCapabilitySet {
        Objects.requireNonNull(values, "values");
        values = values.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(values));
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
