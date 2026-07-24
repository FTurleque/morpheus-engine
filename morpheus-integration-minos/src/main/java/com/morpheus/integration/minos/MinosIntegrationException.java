package com.morpheus.integration.minos;

/** Adapter-local MINOS transport/protocol failure. It must never become a domain dependency. */
public final class MinosIntegrationException extends RuntimeException {
    public MinosIntegrationException(String message) {
        super(message);
    }

    public MinosIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
