package com.morpheus.application.read;

/** Deterministic fail-closed signal that aborts a complete provider ingestion attempt. */
public final class ProviderIngestionLimitException extends IllegalArgumentException {
    public ProviderIngestionLimitException(String message) {
        super(message);
    }
}
