package com.morpheus.application.store;

import com.morpheus.application.sync.ProjectSyncState;
import com.morpheus.application.sync.SourceArchiveRecord;
import com.morpheus.application.sync.SourceInventory;
import com.morpheus.application.sync.SyncStateConflictException;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Storage port for M7 synchronization state, current source inventory and immutable source archives. */
public interface SyncStateStore {
    Optional<ProjectSyncState> findSyncState(ProjectSpecificationId projectId);

    Optional<SourceInventory> findCurrentInventory(ProjectSpecificationId projectId);

    List<SourceArchiveRecord> listArchives(ProjectSpecificationId projectId);

    /**
     * Records an attempt and returns the new revision. {@code expectedRevision} is the revision the caller read
     * (0 when it saw no state); a different current revision is a {@link SyncStateConflictException}.
     */
    long recordAttempt(
            ProjectSpecificationId projectId,
            long expectedRevision,
            Instant attemptedAt,
            Optional<SyncPlan.FullRebuildReason> pendingFullRebuildReason);

    /** Commits a successful sync and returns the new revision, under the same revision rule as {@link #recordAttempt}. */
    long commitSuccessfulSync(
            SourceInventory inventory,
            long expectedRevision,
            SyncPlan.SyncMode mode,
            Instant attemptedAt,
            Instant completedAt,
            Optional<Instant> lastObservedChangeAt,
            List<SourceArchiveRecord> newArchives);
}
