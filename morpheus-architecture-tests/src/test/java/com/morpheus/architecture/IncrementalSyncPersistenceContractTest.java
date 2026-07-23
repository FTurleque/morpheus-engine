package com.morpheus.architecture;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.sync.IncrementalSyncService;
import com.morpheus.application.sync.ProjectSyncState;
import com.morpheus.application.sync.SourceArchiveRecord;
import com.morpheus.application.sync.SourceFingerprint;
import com.morpheus.application.sync.SourceInventory;
import com.morpheus.application.sync.SourceInventoryScanResult;
import com.morpheus.application.sync.SourcePath;
import com.morpheus.application.sync.SourceWatchSignal;
import com.morpheus.application.sync.SyncFreshness;
import com.morpheus.application.sync.SyncFreshnessService;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemorySyncStateStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteSyncStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncrementalSyncPersistenceContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T22:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteProduceSamePlansStateInventoryArchivesAndFreshness() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceInventory baseline = inventory(projectId, "r1", T0,
                entry("move-old.md", "move"), entry("edit.md", "old"), entry("delete.md", "gone"));
        SourceInventory next = inventory(projectId, "r2", T0.plusSeconds(10),
                entry("move-new.md", "move"), entry("edit.md", "new"), entry("add.md", "added"));

        var memoryCore = new MemorySpecificationKnowledgeStore();
        memoryCore.putProject(project(projectId));
        var memoryStore = new MemorySyncStateStore(memoryCore);
        RunResult memory = runTwoSuccessfulSyncs(new IncrementalSyncService(memoryStore), memoryStore, baseline, next);

        Path database = tempDir.resolve("sync-parity.db");
        RunResult sqlite;
        try (var sqliteCore = new SqliteSpecificationKnowledgeStore(database);
             var sqliteStore = new SqliteSyncStateStore(database)) {
            sqliteCore.putProject(project(projectId));
            sqlite = runTwoSuccessfulSyncs(new IncrementalSyncService(sqliteStore), sqliteStore, baseline, next);
        }

        assertEquals(memory, sqlite);
        assertEquals(SyncPlan.SyncMode.INCREMENTAL, memory.secondPlan().mode());
        assertEquals(2, memory.archives().size());
        assertEquals(SyncFreshness.State.FRESH, memory.freshness().state());
    }

    @Test
    void sqliteReopenPreservesCurrentBaselineArchivesAndFreshness() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceInventory baseline = inventory(projectId, "r1", T0, entry("old.md", "same"), entry("gone.md", "gone"));
        SourceInventory next = inventory(projectId, "r2", T0.plusSeconds(10), entry("new.md", "same"));
        Path database = tempDir.resolve("sync-reopen.db");

        RunResult before;
        try (var core = new SqliteSpecificationKnowledgeStore(database);
             var store = new SqliteSyncStateStore(database)) {
            core.putProject(project(projectId));
            before = runTwoSuccessfulSyncs(new IncrementalSyncService(store), store, baseline, next);
        }

        try (var reopened = new SqliteSyncStateStore(database)) {
            assertEquals(before.state(), reopened.findSyncState(projectId).orElseThrow());
            assertEquals(before.inventory(), reopened.findCurrentInventory(projectId).orElseThrow());
            assertEquals(before.archives(), reopened.listArchives(projectId));
            assertEquals(before.freshness(), new SyncFreshnessService(reopened)
                    .assess(projectId, T0.plusSeconds(20), Duration.ofMinutes(5)));
        }
    }

    @Test
    void pendingFullRebuildSurvivesSqliteReopenAndForcesConservativeRetry() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceInventory baseline = inventory(projectId, "r1", T0, entry("a.md", "a"));
        Path database = tempDir.resolve("sync-pending.db");

        try (var core = new SqliteSpecificationKnowledgeStore(database);
             var store = new SqliteSyncStateStore(database)) {
            core.putProject(project(projectId));
            IncrementalSyncService service = new IncrementalSyncService(store);
            SyncPlan initial = service.prepare(SourceInventoryScanResult.complete(baseline), SyncPlan.Trigger.startup(), T0.plusSeconds(1));
            service.complete(initial, T0.plusSeconds(2));
            SyncPlan overflow = service.prepare(
                    SourceInventoryScanResult.complete(inventory(projectId, "r2", T0.plusSeconds(3), entry("a.md", "a"))),
                    SyncPlan.Trigger.watcher(List.of(SourceWatchSignal.overflow())),
                    T0.plusSeconds(4));
            assertEquals(SyncPlan.FullRebuildReason.WATCH_OVERFLOW, overflow.fullRebuildReason().orElseThrow());
        }

        try (var reopened = new SqliteSyncStateStore(database)) {
            assertEquals(SyncFreshness.State.REBUILD_REQUIRED,
                    new SyncFreshnessService(reopened).assess(projectId, T0.plusSeconds(5), Duration.ofMinutes(5)).state());
            SyncPlan retry = new IncrementalSyncService(reopened).prepare(
                    SourceInventoryScanResult.complete(inventory(projectId, "r2", T0.plusSeconds(3), entry("a.md", "a"))),
                    SyncPlan.Trigger.manual(),
                    T0.plusSeconds(6));
            assertEquals(SyncPlan.FullRebuildReason.PREVIOUS_REBUILD_PENDING, retry.fullRebuildReason().orElseThrow());
        }
    }

    @Test
    void successfulFullRebuildClearsPendingReasonAndRestoresFreshness() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var core = new MemorySpecificationKnowledgeStore();
        core.putProject(project(projectId));
        var store = new MemorySyncStateStore(core);
        IncrementalSyncService service = new IncrementalSyncService(store);
        SourceInventory baseline = inventory(projectId, "r1", T0, entry("a.md", "a"));
        SyncPlan initial = service.prepare(SourceInventoryScanResult.complete(baseline), SyncPlan.Trigger.startup(), T0.plusSeconds(1));
        service.complete(initial, T0.plusSeconds(2));

        SourceInventory current = inventory(projectId, "r2", T0.plusSeconds(3), entry("a.md", "b"));
        SyncPlan forced = service.prepare(
                SourceInventoryScanResult.complete(current), SyncPlan.Trigger.manual().forced(), T0.plusSeconds(4));
        assertEquals(SyncPlan.SyncMode.FULL_REBUILD, forced.mode());
        service.complete(forced, T0.plusSeconds(5));

        ProjectSyncState state = store.findSyncState(projectId).orElseThrow();
        assertEquals(Optional.empty(), state.pendingFullRebuildReason());
        assertEquals(SyncPlan.SyncMode.FULL_REBUILD, state.lastSuccessfulMode().orElseThrow());
        assertEquals(SyncFreshness.State.FRESH,
                new SyncFreshnessService(store).assess(projectId, T0.plusSeconds(6), Duration.ofMinutes(1)).state());
    }

    @Test
    void syncStateAdaptersRejectUnknownProject() {
        ProjectSpecificationId unknown = ProjectSpecificationId.generate();
        var memoryCore = new MemorySpecificationKnowledgeStore();
        var memoryStore = new MemorySyncStateStore(memoryCore);
        assertThrows(KnowledgeStoreException.class, () ->
                memoryStore.recordAttempt(unknown, T0, Optional.empty()));

        Path database = tempDir.resolve("unknown-project.db");
        try (var sqliteStore = new SqliteSyncStateStore(database)) {
            assertThrows(KnowledgeStoreException.class, () ->
                    sqliteStore.recordAttempt(unknown, T0, Optional.empty()));
        }
    }

    private RunResult runTwoSuccessfulSyncs(
            IncrementalSyncService service,
            com.morpheus.application.store.SyncStateStore store,
            SourceInventory baseline,
            SourceInventory next) {
        SyncPlan initial = service.prepare(
                SourceInventoryScanResult.complete(baseline), SyncPlan.Trigger.startup(), T0.plusSeconds(1));
        service.complete(initial, T0.plusSeconds(2));
        SyncPlan second = service.prepare(
                SourceInventoryScanResult.complete(next), SyncPlan.Trigger.manual(), T0.plusSeconds(11));
        service.complete(second, T0.plusSeconds(12));
        return new RunResult(
                second,
                store.findSyncState(next.projectId()).orElseThrow(),
                store.findCurrentInventory(next.projectId()).orElseThrow(),
                store.listArchives(next.projectId()),
                new SyncFreshnessService(store).assess(next.projectId(), T0.plusSeconds(20), Duration.ofMinutes(5)));
    }

    private ProjectStoreEntry project(ProjectSpecificationId id) {
        return new ProjectStoreEntry(id, SourceLocator.file("workspace-" + id));
    }

    private static SourceInventory inventory(
            ProjectSpecificationId projectId,
            String revision,
            Instant capturedAt,
            SourceInventory.Entry... entries) {
        return new SourceInventory(projectId, Optional.of(revision), capturedAt, List.of(entries));
    }

    private static SourceInventory.Entry entry(String path, String content) {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new SourceInventory.Entry(new SourcePath(path), SourceFingerprint.ofBytes(bytes), bytes.length);
    }

    private record RunResult(
            SyncPlan secondPlan,
            ProjectSyncState state,
            SourceInventory inventory,
            List<SourceArchiveRecord> archives,
            SyncFreshness freshness) {
    }
}
