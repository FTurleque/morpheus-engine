package com.morpheus.domain.provider;

/** Result of probing a concrete source with a provider. */
public enum ProviderProbeStatus {
    SUPPORTED,
    UNSUPPORTED,
    AMBIGUOUS,
    INVALID
}
