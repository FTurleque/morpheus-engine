package com.morpheus.application.product;

/**
 * Trust classification attached to update-discovery results.
 *
 * <p>MORPHEUS currently performs discovery only: it validates transport and manifest shape, but it does not
 * cryptographically verify the advertised provenance attestation. No installation decision may treat
 * {@link #DISCOVERY_ONLY} as publisher verification.</p>
 */
public enum UpdateTrustLevel {
    DISCOVERY_ONLY
}
