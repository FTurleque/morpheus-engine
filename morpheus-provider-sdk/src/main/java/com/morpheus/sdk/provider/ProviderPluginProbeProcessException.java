package com.morpheus.sdk.provider;

/** Controlled failure of the isolated provider-plugin probe JVM. */
final class ProviderPluginProbeProcessException extends RuntimeException {
    private final boolean timeout;

    ProviderPluginProbeProcessException(String message, boolean timeout) {
        super(message);
        this.timeout = timeout;
    }

    ProviderPluginProbeProcessException(String message, boolean timeout, Throwable cause) {
        super(message, cause);
        this.timeout = timeout;
    }

    boolean timeout() {
        return timeout;
    }
}
