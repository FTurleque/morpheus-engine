package com.morpheus.application.reference;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Registry of optional external-system resolvers keyed by normalized system name. */
public final class ExternalReferenceResolverRegistry {
    private final Map<String, ExternalReferenceResolver> resolvers;

    public ExternalReferenceResolverRegistry(Collection<? extends ExternalReferenceResolver> resolvers) {
        Objects.requireNonNull(resolvers, "resolvers");
        Map<String, ExternalReferenceResolver> indexed = new LinkedHashMap<>();
        for (ExternalReferenceResolver resolver : resolvers) {
            Objects.requireNonNull(resolver, "resolver");
            String system = normalizeSystem(resolver.system());
            ExternalReferenceResolver previous = indexed.putIfAbsent(system, resolver);
            if (previous != null) {
                throw new IllegalArgumentException("multiple external reference resolvers for system: " + system);
            }
        }
        this.resolvers = Map.copyOf(indexed);
    }

    public Optional<ExternalReferenceResolver> find(String system) {
        return Optional.ofNullable(resolvers.get(normalizeSystem(system)));
    }

    private String normalizeSystem(String value) {
        Objects.requireNonNull(value, "system");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("system must not be blank");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }
}
