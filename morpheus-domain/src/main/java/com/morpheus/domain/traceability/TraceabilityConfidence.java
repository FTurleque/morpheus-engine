package com.morpheus.domain.traceability;

/** Confidence associated with a traceability observation, inclusively bounded to [0.0, 1.0]. */
public record TraceabilityConfidence(double value) {
    public TraceabilityConfidence {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException("traceability confidence must be finite and between 0.0 and 1.0");
        }
    }

    public static TraceabilityConfidence of(double value) {
        return new TraceabilityConfidence(value);
    }
}
