package com.morpheus.domain.temporal;

/**
 * Temporal position of a versioned domain occurrence.
 *
 * <p>This dimension is intentionally orthogonal to change lifecycle, snapshot state and
 * verification/resolution states.</p>
 */
public enum TemporalState {
    CURRENT,
    PROPOSED,
    HISTORICAL
}
