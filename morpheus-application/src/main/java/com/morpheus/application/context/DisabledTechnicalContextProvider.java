package com.morpheus.application.context;

import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;

import java.util.Map;
import java.util.Objects;

/** Generic no-op provider used when an optional technical-context engine is not configured. */
public final class DisabledTechnicalContextProvider implements TechnicalContextProvider, ExternalIntegrationStatusProvider {
    private final String system;
    private final String message;

    public DisabledTechnicalContextProvider(String system, String message) {
        this.system = requireText(system, "system");
        this.message = requireText(message, "message");
    }

    @Override
    public String system() {
        return system;
    }

    @Override
    public ExternalIntegrationStatus status() {
        return new ExternalIntegrationStatus(system, "DISABLED", false, message, Map.of());
    }

    @Override
    public TechnicalContextObservation build(TechnicalContextRequest request) {
        Objects.requireNonNull(request, "request");
        return TechnicalContextObservation.unavailable(status());
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
