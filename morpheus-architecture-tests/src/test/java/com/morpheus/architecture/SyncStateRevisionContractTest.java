package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SyncStateStore;
import com.morpheus.application.sync.IncrementalSyncService;
import com.morpheus.application.sync.ProjectSyncState;
import com.morpheus.application.sync.SourceFingerprint;
import com.morpheus.application.sync.SourceInventory;
import com.morpheus.application.sync.SourceInventoryScanResult;
import com.morpheus.application.sync.SourcePath;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.application.sync.SyncStateConflictException;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemorySyncStateStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteSyncStateStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Synchronization state is written with the revision the writer read, and both adapters hold that invariant.
 *
 * <p>Two syncs on one project open one SQLite connection each, so the adapter's {@code synchronized} never
 * excluded them from each other. The write was a blind upsert: a writer that had read the state before another
 * recorded {@code SCAN_INCOMPLETE} wrote {@code NULL} over that flag, and the next {@code prepare()} chose the
 * incremental path on an inventory the system knew was incomplete. The scenario is exercised on the memory store
 * and on SQLite, because the memory store is the one the unit tests always used and the defect was only visible
 * on the other.</p>
 */
class SyncStateRevisionContractTest {
    private static final Instant T0 = Instant.parse("2026-09-25T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void aStaleAttemptCannotEraseAPendingRebuildFlagOnEitherAdapter() {
        onBothAdapters(store -> {
            ProjectSpecificationId project = ProjectSpecificationId.generate();
            register(store, project);
            long first = store.store().recordAttempt(project, 0L, T0, Optional.empty());
            assertEquals(1L, first);

            long a = store.store().recordAttempt(project, first, T0.plusSeconds(1),
                    Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE));
            SyncStateConflictException stale = assertThrows(SyncStateConflictException.class,
                    () -> store.store().recordAttempt(project, first, T0.plusSeconds(2), Optional.empty()));

            assertEquals(2L, a);
            assertEquals(first, stale.expectedRevision());
            assertEquals(a, stale.currentRevision());
            ProjectSyncState persisted = store.store().findSyncState(project).orElseThrow();
            assertEquals(Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE), persisted.pendingFullRebuildReason(),
                    "the stale writer must not clear the flag");
            assertEquals(a, persisted.revision());
        });
    }

    @Test
    void aStaleCommitIsRefusedAndLeavesTheStateAndTheInventoryUntouched() {
        onBothAdapters(store -> {
            ProjectSpecificationId project = ProjectSpecificationId.generate();
            register(store, project);
            long first = store.store().recordAttempt(project, 0L, T0, Optional.empty());
            store.store().recordAttempt(project, first, T0.plusSeconds(1),
                    Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE));

            assertThrows(SyncStateConflictException.class, () -> store.store().commitSuccessfulSync(
                    inventory(project, "r1"), first, SyncPlan.SyncMode.INCREMENTAL, T0, T0.plusSeconds(3),
                    Optional.empty(), List.of()));

            assertEquals(Optional.empty(), store.store().findCurrentInventory(project),
                    "a refused commit must not replace the inventory");
            assertEquals(Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE),
                    store.store().findSyncState(project).orElseThrow().pendingFullRebuildReason());
        });
    }

    @Test
    void aFirstWriteClaimsTheRowAndASecondFirstWriteIsAConflict() {
        onBothAdapters(store -> {
            ProjectSpecificationId project = ProjectSpecificationId.generate();
            register(store, project);

            assertEquals(1L, store.store().recordAttempt(project, 0L, T0, Optional.empty()));
            SyncStateConflictException second = assertThrows(SyncStateConflictException.class,
                    () -> store.store().recordAttempt(project, 0L, T0, Optional.empty()));

            assertEquals(1L, second.currentRevision());
        });
    }

    @Test
    void everyWriteAdvancesTheRevisionByExactlyOneAndReturnsIt() {
        onBothAdapters(store -> {
            ProjectSpecificationId project = ProjectSpecificationId.generate();
            register(store, project);

            long attempted = store.store().recordAttempt(project, 0L, T0, Optional.empty());
            long committed = store.store().commitSuccessfulSync(
                    inventory(project, "r1"), attempted, SyncPlan.SyncMode.FULL_REBUILD, T0, T0.plusSeconds(1),
                    Optional.empty(), List.of());

            assertEquals(1L, attempted);
            assertEquals(2L, committed);
            assertEquals(committed, store.store().findSyncState(project).orElseThrow().revision());
        });
    }

    @Test
    void aSuccessfulSyncOfAnOvertakenPlanDoesNotClearScanIncompleteAndTheNextPrepareRebuilds() {
        onBothAdapters(store -> {
            ProjectSpecificationId project = ProjectSpecificationId.generate();
            register(store, project);
            IncrementalSyncService service = new IncrementalSyncService(store.store());
            SyncPlan baseline = service.prepare(
                    SourceInventoryScanResult.complete(inventory(project, "r1")), SyncPlan.Trigger.startup(), T0);
            service.complete(baseline, T0.plusSeconds(1));

            SyncPlan b = service.prepare(
                    SourceInventoryScanResult.complete(inventory(project, "r2")), SyncPlan.Trigger.manual(),
                    T0.plusSeconds(2));
            SyncPlan a = service.prepare(
                    SourceInventoryScanResult.incomplete(project, List.of(new SourceInventoryScanResult.Failure(
                            Optional.empty(), SourceInventoryScanResult.Failure.Code.SCAN_LIMIT_EXCEEDED, "too many sources"))),
                    SyncPlan.Trigger.manual(),
                    T0.plusSeconds(3));
            assertEquals(Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE), a.fullRebuildReason());

            assertThrows(SyncStateConflictException.class, () -> service.complete(b, T0.plusSeconds(4)));

            assertEquals(Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE),
                    store.store().findSyncState(project).orElseThrow().pendingFullRebuildReason(),
                    "B started from an earlier revision and must not clear what A recorded");
            SyncPlan next = service.prepare(
                    SourceInventoryScanResult.complete(inventory(project, "r2")), SyncPlan.Trigger.manual(),
                    T0.plusSeconds(5));
            assertEquals(SyncPlan.SyncMode.FULL_REBUILD, next.mode());
        });
    }

    @Test
    void aFailingOvertakenPlanCannotOverwriteTheNewerStateEither() {
        onBothAdapters(store -> {
            ProjectSpecificationId project = ProjectSpecificationId.generate();
            register(store, project);
            IncrementalSyncService service = new IncrementalSyncService(store.store());
            SyncPlan older = service.prepare(
                    SourceInventoryScanResult.complete(inventory(project, "r1")), SyncPlan.Trigger.startup(), T0);
            SyncPlan newer = service.prepare(
                    SourceInventoryScanResult.complete(inventory(project, "r1")), SyncPlan.Trigger.startup(),
                    T0.plusSeconds(1));

            assertThrows(SyncStateConflictException.class, () -> service.fail(older, T0.plusSeconds(2)));

            assertEquals(newer.stateRevision(), store.store().findSyncState(project).orElseThrow().revision());
            assertTrue(older.stateRevision() < newer.stateRevision());
        });
    }

    private void onBothAdapters(Consumer<Adapter> scenario) {
        var memoryCore = new MemorySpecificationKnowledgeStore();
        scenario.accept(new Adapter(new MemorySyncStateStore(memoryCore), memoryCore::putProject));

        Path database = tempDir.resolve("sync-revision.db");
        try (var sqliteCore = new SqliteSpecificationKnowledgeStore(database);
             var sqliteStore = new SqliteSyncStateStore(database)) {
            scenario.accept(new Adapter(sqliteStore, sqliteCore::putProject));
        }
    }

    private static void register(Adapter adapter, ProjectSpecificationId project) {
        adapter.registerProject().accept(new ProjectStoreEntry(project, SourceLocator.file("workspace-" + project)));
    }

    private static SourceInventory inventory(ProjectSpecificationId project, String revision) {
        byte[] bytes = "a".getBytes(StandardCharsets.UTF_8);
        return new SourceInventory(project, Optional.of(revision), T0, List.of(new SourceInventory.Entry(
                new SourcePath("a.md"), SourceFingerprint.ofBytes(bytes), bytes.length)));
    }

    private record Adapter(SyncStateStore store, Consumer<ProjectStoreEntry> registerProject) {
    }
}
