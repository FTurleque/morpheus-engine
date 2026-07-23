package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Explainable freshness status calculated from persisted sync state and an explicit clock value. */
public record SyncFreshness(
        ProjectSpecificationId projectId,
        State state,
        Instant asOf,
        Duration maxAge,
        Optional<Duration> ageSinceSuccessfulSync,
        Optional<Instant> lastSuccessfulSyncAt,
        Optional<Instant> lastObservedChangeAt,
        Optional<String> sourceRevision,
        Optional<SyncPlan.SyncMode> lastSuccessfulMode,
        Optional<SyncPlan.FullRebuildReason> pendingFullRebuildReason,
        int currentSourceCount) {

    public SyncFreshness {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(maxAge, "maxAge");
        ageSinceSuccessfulSync = Objects.requireNonNull(ageSinceSuccessfulSync, "ageSinceSuccessfulSync");
        lastSuccessfulSyncAt = Objects.requireNonNull(lastSuccessfulSyncAt, "lastSuccessfulSyncAt");
        lastObservedChangeAt = Objects.requireNonNull(lastObservedChangeAt, "lastObservedChangeAt");
        sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision");
        lastSuccessfulMode = Objects.requireNonNull(lastSuccessfulMode, "lastSuccessfulMode");
        pendingFullRebuildReason = Objects.requireNonNull(pendingFullRebuildReason, "pendingFullRebuildReason");
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("maxAge must be > 0");
        }
        if (currentSourceCount < 0) {
            throw new IllegalArgumentException("currentSourceCount must be >= 0");
        }
        if (lastSuccessfulSyncAt.isPresent() != ageSinceSuccessfulSync.isPresent()) {
            throw new IllegalArgumentException("age requires a successful sync timestamp");
        }
        if (state == State.UNKNOWN && lastSuccessfulSyncAt.isPresent()) {
            throw new IllegalArgumentException("UNKNOWN must not have a successful sync");
        }
        if (state == State.REBUILD_REQUIRED && pendingFullRebuildReason.isEmpty()) {
            throw new IllegalArgumentException("REBUILD_REQUIRED requires a pending reason");
        }
    }

    public enum State {
        UNKNOWN,
        FRESH,
        STALE,
        REBUILD_REQUIRED
    }
}
