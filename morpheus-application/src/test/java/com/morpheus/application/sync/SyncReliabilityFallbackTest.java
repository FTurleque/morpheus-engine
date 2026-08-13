package com.morpheus.application.sync;

import com.morpheus.application.store.SyncStateStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SyncReliabilityFallbackTest {
    private static final Instant T0 = Instant.parse("2026-07-23T22:30:00Z");

    @Test
    void sourceRevisionIsOpaqueAndMayChangeLexicallyBackwardWithoutForcingRebuild() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceInventory baseline = inventory(projectId, "revision-9", T0, "same");
        StubStore store = StubStore.consistent(baseline);

        SyncPlan plan = new IncrementalSyncService(store).prepare(
                SourceInventoryScanResult.complete(inventory(projectId, "revision-1", T0.plusSeconds(1), "same")),
                SyncPlan.Trigger.manual(),
                T0.plusSeconds(2));

        assertEquals(SyncPlan.SyncMode.INCREMENTAL, plan.mode());
        assertEquals(Optional.empty(), plan.fullRebuildReason());
    }

    @Test
    void inconsistentPersistedBaselineForcesFullRebuild() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceInventory baseline = inventory(projectId, "r1", T0, "same");
        StubStore store = new StubStore(
                Optional.of(new ProjectSyncState(
                        projectId,
                        Optional.of(T0),
                        Optional.of(T0),
                        Optional.empty(),
                        Optional.of("r1"),
                        Optional.of(SyncPlan.SyncMode.FULL_REBUILD),
                        Optional.empty(),
                        99)),
                Optional.of(baseline));

        SyncPlan plan = new IncrementalSyncService(store).prepare(
                SourceInventoryScanResult.complete(inventory(projectId, "r2", T0.plusSeconds(1), "same")),
                SyncPlan.Trigger.manual(),
                T0.plusSeconds(2));

        assertEquals(SyncPlan.SyncMode.FULL_REBUILD, plan.mode());
        assertEquals(SyncPlan.FullRebuildReason.BASELINE_INCONSISTENT, plan.fullRebuildReason().orElseThrow());
    }

    @Test
    void committedStateThatThrowsAfterCommitIsReconciledAsSuccess() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        StubStore store = new StubStore(Optional.empty(), Optional.empty());
        IncrementalSyncService service = new IncrementalSyncService(store);
        SourceInventory current = inventory(projectId, "r1", T0, "content");
        SyncPlan plan = service.prepare(
                SourceInventoryScanResult.complete(current),
                SyncPlan.Trigger.manual().forced(),
                T0.plusSeconds(1));
        store.throwAfterCommit = true;

        service.complete(plan, T0.plusSeconds(2));

        assertEquals(1, store.commitAttempts);
        assertEquals(Optional.empty(), store.state.orElseThrow().pendingFullRebuildReason());
        assertEquals(Optional.of(current), store.inventory);
    }

    @Test
    void transientPostPublicationStateFailureIsRetriedOnce() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        StubStore store = new StubStore(Optional.empty(), Optional.empty());
        IncrementalSyncService service = new IncrementalSyncService(store);
        SourceInventory current = inventory(projectId, "r1", T0, "content");
        SyncPlan plan = service.prepare(
                SourceInventoryScanResult.complete(current),
                SyncPlan.Trigger.manual().forced(),
                T0.plusSeconds(1));
        store.failBeforeCommit = 1;

        service.complete(plan, T0.plusSeconds(2));

        assertEquals(2, store.commitAttempts);
        assertEquals(Optional.empty(), store.state.orElseThrow().pendingFullRebuildReason());
        assertEquals(Optional.of(current), store.inventory);
    }

    @Test
    void repeatedPostPublicationStateFailureMarksBaselineInconsistentInsteadOfExecutionFailed() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        StubStore store = new StubStore(Optional.empty(), Optional.empty());
        IncrementalSyncService service = new IncrementalSyncService(store);
        SourceInventory current = inventory(projectId, "r1", T0, "content");
        SyncPlan plan = service.prepare(
                SourceInventoryScanResult.complete(current),
                SyncPlan.Trigger.manual().forced(),
                T0.plusSeconds(1));
        store.failBeforeCommit = 2;

        service.complete(plan, T0.plusSeconds(2));

        assertEquals(2, store.commitAttempts);
        assertEquals(
                Optional.of(SyncPlan.FullRebuildReason.BASELINE_INCONSISTENT),
                store.state.orElseThrow().pendingFullRebuildReason());
    }

    @Test
    void noChangeIncrementalCompletionStillPropagatesPersistentStateFailure() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceInventory baseline = inventory(projectId, "r1", T0, "same");
        StubStore store = StubStore.consistent(baseline);
        IncrementalSyncService service = new IncrementalSyncService(store);
        SourceInventory current = inventory(projectId, "r1", T0.plusSeconds(1), "same");
        SyncPlan plan = service.prepare(
                SourceInventoryScanResult.complete(current),
                SyncPlan.Trigger.manual(),
                T0.plusSeconds(2));
        store.failBeforeCommit = 2;

        assertThrows(IllegalStateException.class, () -> service.complete(plan, T0.plusSeconds(3)));
        assertEquals(2, store.commitAttempts);
    }

    @Test
    void failPreservesScanIncompleteReason() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        StubStore store = new StubStore(Optional.empty(), Optional.empty());
        IncrementalSyncService service = new IncrementalSyncService(store);
        SyncPlan plan = service.prepare(
                SourceInventoryScanResult.incomplete(
                        projectId,
                        List.of(new SourceInventoryScanResult.Failure(Optional.of("openspec"), "cannot scan"))),
                SyncPlan.Trigger.manual(),
                T0.plusSeconds(1));

        service.fail(plan, T0.plusSeconds(2));

        assertEquals(
                Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE),
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

    private static final class StubStore implements SyncStateStore {
        private Optional<ProjectSyncState> state;
        private Optional<SourceInventory> inventory;
        private int failBeforeCommit;
        private boolean throwAfterCommit;
        private int commitAttempts;

        private StubStore(Optional<ProjectSyncState> state, Optional<SourceInventory> inventory) {
            this.state = state;
            this.inventory = inventory;
        }

        static StubStore consistent(SourceInventory inventory) {
            return new StubStore(
                    Optional.of(new ProjectSyncState(
                            inventory.projectId(),
                            Optional.of(inventory.capturedAt()),
                            Optional.of(inventory.capturedAt()),
                            Optional.empty(),
                            inventory.sourceRevision(),
                            Optional.of(SyncPlan.SyncMode.FULL_REBUILD),
                            Optional.empty(),
                            inventory.entries().size())),
                    Optional.of(inventory));
        }

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
            commitAttempts++;
            if (failBeforeCommit > 0) {
                failBeforeCommit--;
                throw new IllegalStateException("synthetic pre-commit failure");
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
            if (throwAfterCommit) {
                throw new IllegalStateException("synthetic committed cleanup failure");
            }
        }
    }
}
