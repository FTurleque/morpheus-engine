package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Persisted synchronization status independent from published knowledge snapshots. */
public record ProjectSyncState(
        ProjectSpecificationId projectId,
        Optional<Instant> lastAttemptAt,
        Optional<Instant> lastSuccessfulSyncAt,
        Optional<Instant> lastObservedChangeAt,
        Optional<String> sourceRevision,
        Optional<SyncPlan.SyncMode> lastSuccessfulMode,
        Optional<SyncPlan.FullRebuildReason> pendingFullRebuildReason,
        int currentSourceCount) {

    public ProjectSyncState {
        Objects.requireNonNull(projectId, "projectId");
        lastAttemptAt = Objects.requireNonNull(lastAttemptAt, "lastAttemptAt");
        lastSuccessfulSyncAt = Objects.requireNonNull(lastSuccessfulSyncAt, "lastSuccessfulSyncAt");
        lastObservedChangeAt = Objects.requireNonNull(lastObservedChangeAt, "lastObservedChangeAt");
        sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision").map(String::trim);
        lastSuccessfulMode = Objects.requireNonNull(lastSuccessfulMode, "lastSuccessfulMode");
        pendingFullRebuildReason = Objects.requireNonNull(pendingFullRebuildReason, "pendingFullRebuildReason");
        if (currentSourceCount < 0) {
            throw new IllegalArgumentException("currentSourceCount must be >= 0");
        }
        if (lastSuccessfulSyncAt.isPresent() != lastSuccessfulMode.isPresent()) {
            throw new IllegalArgumentException("successful sync timestamp and mode must be present together");
        }
        if (lastObservedChangeAt.isPresent() && lastSuccessfulSyncAt.isEmpty()) {
            throw new IllegalArgumentException("observed change requires a successful sync");
        }
        if (lastAttemptAt.isPresent() && lastSuccessfulSyncAt.isPresent()
                && lastAttemptAt.orElseThrow().isAfter(lastSuccessfulSyncAt.orElseThrow())) {
            // This is valid only when a newer failed/pending attempt exists.
            if (pendingFullRebuildReason.isEmpty()) {
                throw new IllegalArgumentException("attempt after last success requires pending rebuild reason");
            }
        }
    }

    public static ProjectSyncState empty(ProjectSpecificationId projectId) {
        return new ProjectSyncState(
                projectId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0);
    }
}
