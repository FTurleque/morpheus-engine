package com.morpheus.application.reference;

import java.util.Map;
import java.util.Objects;

/** Adapter-neutral availability report for one optional external-system integration. */
public record ExternalIntegrationStatus(
        String system,
        String state,
        boolean configured,
        String message,
        Map<String, String> details) {

    public ExternalIntegrationStatus {
        system = requireText(system, "system");
        state = requireText(state, "state");
        message = requireText(message, "message");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
