package com.morpheus.integration.nexus;

/** Adapter-local failure; callers translate it into an explicit optional-integration state. */
public final class NexusIntegrationException extends RuntimeException {
    public NexusIntegrationException(String message) {
        super(message);
    }

    public NexusIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
