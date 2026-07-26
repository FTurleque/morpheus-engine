package com.morpheus.application.composition;

import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;

/** JSON-safe provider observation retained in a composition conflict. */
public record CompositionCandidate(
        ProviderId providerId,
        int priority,
        String value,
        String source,
        String evidenceId) {

    public CompositionCandidate {
        Objects.requireNonNull(providerId, "providerId");
        value = requireNonBlank(value, "value");
        source = requireNonBlank(source, "source");
        evidenceId = requireNonBlank(evidenceId, "evidenceId");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
