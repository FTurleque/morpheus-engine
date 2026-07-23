package com.morpheus.application.store;

import com.morpheus.application.sync.ProjectSyncState;
import com.morpheus.application.sync.SourceArchiveRecord;
import com.morpheus.application.sync.SourceInventory;
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

    void recordAttempt(
            ProjectSpecificationId projectId,
            Instant attemptedAt,
            Optional<SyncPlan.FullRebuildReason> pendingFullRebuildReason);

    void commitSuccessfulSync(
            SourceInventory inventory,
            SyncPlan.SyncMode mode,
            Instant attemptedAt,
            Instant completedAt,
            Optional<Instant> lastObservedChangeAt,
            List<SourceArchiveRecord> newArchives);
}
