package com.morpheus.application.snapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Prevents recovery from racing a fresh candidate owned by another local process. */
public record SnapshotRecoveryPolicy(Duration staleAfter) {
    private static final Duration DEFAULT_STALE_AFTER = Duration.ofMinutes(10);

    public SnapshotRecoveryPolicy {
        Objects.requireNonNull(staleAfter, "staleAfter");
        if (staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalArgumentException("staleAfter must be greater than zero");
        }
    }

    public static SnapshotRecoveryPolicy safeDefaults() {
        return new SnapshotRecoveryPolicy(DEFAULT_STALE_AFTER);
    }

    public Instant cutoffAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return now.minus(staleAfter);
    }
}
