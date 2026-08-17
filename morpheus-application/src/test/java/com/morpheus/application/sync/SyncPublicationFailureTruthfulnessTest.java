package com.morpheus.application.sync;

import com.morpheus.application.store.SyncStateStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SyncPublicationFailureTruthfulnessTest {
    private static final Instant T0 = Instant.parse("2026-08-17T12:00:00Z");

    @Test
    void outerFailureHandlingNeverDowngradesPossiblePublicationToExecutionFailed() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        FaultInjectingStore store = new FaultInjectingStore();
        IncrementalSyncService service = new IncrementalSyncService(store);
        SourceInventory current = inventory(projectId, "r1", T0, "content");
        SyncPlan plan = service.prepare(
                SourceInventoryScanResult.complete(current),
                SyncPlan.Trigger.manual().forced(),
                T0.plusSeconds(1));

        store.commitFailuresRemaining = 2;
        store.recordAttemptFailuresRemaining = 1;

        assertThrows(IllegalStateException.class, () -> service.complete(plan, T0.plusSeconds(2)));

        // Simulate the storage layer becoming writable again before the adapter's outer catch calls fail().
        service.fail(plan, T0.plusSeconds(3));

        assertEquals(
                Optional.of(SyncPlan.FullRebuildReason.BASELINE_INCONSISTENT),
                store.state.orElseThrow().pendingFullRebuildReason());
    }

    private static SourceInventory inventory(
            ProjectSpecificationId projectId,
            String revision,
            Instant capturedAt,
            String content) {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new SourceInventory(
                projectId,
                Optional.of(revision),
                capturedAt,
                List.of(new SourceInventory.Entry(
                        new SourcePath("a.md"),
                        SourceFingerprint.ofBytes(bytes),
                        bytes.length)));
    }

    private static final class FaultInjectingStore implements SyncStateStore {
        private Optional<ProjectSyncState> state = Optional.empty();
        private Optional<SourceInventory> inventory = Optional.empty();
        private int commitFailuresRemaining;
        private int recordAttemptFailuresRemaining;

        @Override
        public Optional<ProjectSyncState> findSyncState(ProjectSpecificationId projectId) {
            return state;
        }

        @Override
        public Optional<SourceInventory> findCurrentInventory(ProjectSpecificationId projectId) {
            return inventory;
        }

        @Override
        public List<SourceArchiveRecord> listArchives(ProjectSpecificationId projectId) {
            return List.of();
        }

        @Override
        public void recordAttempt(
                ProjectSpecificationId projectId,
                Instant attemptedAt,
                Optional<SyncPlan.FullRebuildReason> pendingFullRebuildReason) {
            if (recordAttemptFailuresRemaining > 0) {
                recordAttemptFailuresRemaining--;
                throw new IllegalStateException("synthetic attempt-marker failure");
            }
            ProjectSyncState previous = state.orElse(ProjectSyncState.empty(projectId));
            state = Optional.of(new ProjectSyncState(
                    projectId,
                    Optional.of(attemptedAt),
                    previous.lastSuccessfulSyncAt(),
                    previous.lastObservedChangeAt(),
                    previous.sourceRevision(),
                    previous.lastSuccessfulMode(),
                    pendingFullRebuildReason,
                    previous.currentSourceCount()));
        }

        @Override
        public void commitSuccessfulSync(
                SourceInventory inventory,
                SyncPlan.SyncMode mode,
                Instant attemptedAt,
                Instant completedAt,
                Optional<Instant> lastObservedChangeAt,
                List<SourceArchiveRecord> newArchives) {
            if (commitFailuresRemaining > 0) {
                commitFailuresRemaining--;
                throw new IllegalStateException("synthetic sync-state commit failure");
            }
            this.inventory = Optional.of(inventory);
            state = Optional.of(new ProjectSyncState(
                    inventory.projectId(),
                    Optional.of(attemptedAt),
                    Optional.of(completedAt),
                    lastObservedChangeAt,
                    inventory.sourceRevision(),
                    Optional.of(mode),
                    Optional.empty(),
                    inventory.entries().size()));
        }
    }
}
