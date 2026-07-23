package com.morpheus.application.sync;

import com.morpheus.application.store.SyncStateStore;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Calculates freshness without hidden wall-clock reads. */
public final class SyncFreshnessService {
    private final SyncStateStore store;

    public SyncFreshnessService(SyncStateStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public SyncFreshness assess(ProjectSpecificationId projectId, Instant now, Duration maxAge) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("maxAge must be > 0");
        }

        Optional<ProjectSyncState> state = store.findSyncState(projectId);
        if (state.isEmpty() || state.orElseThrow().lastSuccessfulSyncAt().isEmpty()) {
            ProjectSyncState value = state.orElseGet(() -> ProjectSyncState.empty(projectId));
            return new SyncFreshness(
                    projectId,
                    value.pendingFullRebuildReason().isPresent()
                            ? SyncFreshness.State.REBUILD_REQUIRED
                            : SyncFreshness.State.UNKNOWN,
                    now,
                    maxAge,
                    Optional.empty(),
                    Optional.empty(),
                    value.lastObservedChangeAt(),
                    value.sourceRevision(),
                    value.lastSuccessfulMode(),
                    value.pendingFullRebuildReason(),
                    value.currentSourceCount());
        }

        ProjectSyncState value = state.orElseThrow();
        Instant lastSuccess = value.lastSuccessfulSyncAt().orElseThrow();
        if (now.isBefore(lastSuccess)) {
            throw new IllegalArgumentException("now must not be before last successful sync");
        }
        Duration age = Duration.between(lastSuccess, now);
        SyncFreshness.State freshnessState;
        if (value.pendingFullRebuildReason().isPresent()) {
            freshnessState = SyncFreshness.State.REBUILD_REQUIRED;
        } else if (age.compareTo(maxAge) <= 0) {
            freshnessState = SyncFreshness.State.FRESH;
        } else {
            freshnessState = SyncFreshness.State.STALE;
        }
        return new SyncFreshness(
                projectId,
                freshnessState,
                now,
                maxAge,
                Optional.of(age),
                Optional.of(lastSuccess),
                value.lastObservedChangeAt(),
                value.sourceRevision(),
                value.lastSuccessfulMode(),
                value.pendingFullRebuildReason(),
                value.currentSourceCount());
    }
}
