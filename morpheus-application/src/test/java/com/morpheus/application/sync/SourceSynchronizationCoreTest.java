package com.morpheus.application.sync;

import com.morpheus.application.store.SyncStateStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceSynchronizationCoreTest {
    private static final Instant T0 = Instant.parse("2026-07-23T21:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void sourcePathIsCanonicalAndCannotEscapeProjectRoot() {
        assertEquals("specs/a.md", new SourcePath("./specs\\a.md").toString());
        assertThrows(IllegalArgumentException.class, () -> new SourcePath("../secret.md"));
        assertThrows(IllegalArgumentException.class, () -> new SourcePath("C:\\secret.md"));
        assertThrows(IllegalArgumentException.class, () -> new SourcePath("/secret.md"));
    }

    @Test
    void scannerUsesContentSha256AndOnlySelectedRoots() throws Exception {
        Path specs = Files.createDirectories(tempDir.resolve("specs"));
        Files.createDirectories(tempDir.resolve("other"));
        Files.writeString(specs.resolve("a.md"), "alpha");
        Files.writeString(tempDir.resolve("other/b.md"), "ignored");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner();

        SourceInventory first = scanner.scan(
                        tempDir, projectId, Optional.of("rev-1"), T0, List.of(Path.of("specs")))
                .inventory().orElseThrow();
        assertEquals(List.of("specs/a.md"), first.entries().stream().map(e -> e.path().toString()).toList());
        assertEquals(SourceFingerprint.ofBytes("alpha".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                first.entries().getFirst().fingerprint());

        Files.writeString(specs.resolve("a.md"), "omega");
        SourceInventory second = scanner.scan(
                        tempDir, projectId, Optional.of("rev-2"), T0.plusSeconds(1), List.of(Path.of("specs")))
                .inventory().orElseThrow();
        assertNotEquals(first.entries().getFirst().fingerprint(), second.entries().getFirst().fingerprint());
    }

    @Test
    void missingSourceRootProducesIncompleteScanInsteadOfPartialBaseline() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceInventoryScanResult result = new LocalSourceInventoryScanner().scan(
                tempDir, projectId, Optional.empty(), T0, List.of(Path.of("missing")));
        assertFalse(result.complete());
        assertTrue(result.inventory().isEmpty());
        assertEquals(1, result.failures().size());
    }

    @Test
    void differClassifiesStableAddedModifiedAndDeletedSources() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceInventory before = inventory(projectId, "r1", T0,
                entry("a.md", "a"), entry("b.md", "before"), entry("gone.md", "gone"));
        SourceInventory after = inventory(projectId, "r2", T0.plusSeconds(1),
                entry("a.md", "a"), entry("b.md", "after"), entry("new.md", "new"));

        SourceInventoryDiff diff = new SourceInventoryDiffer().diff(before, after);
        assertEquals(1, count(diff, SourceInventoryDiff.ChangeKind.UNCHANGED));
        assertEquals(1, count(diff, SourceInventoryDiff.ChangeKind.MODIFIED));
        assertEquals(1, count(diff, SourceInventoryDiff.ChangeKind.DELETED));
        assertEquals(1, count(diff, SourceInventoryDiff.ChangeKind.ADDED));
        assertFalse(diff.ambiguousMoves());
    }

    @Test
    void uniqueContentMatchIsMoveButAmbiguousMatchIsNeverGuessed() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceInventory before = inventory(projectId, "r1", T0, entry("old.md", "same"));
        SourceInventory after = inventory(projectId, "r2", T0.plusSeconds(1), entry("new.md", "same"));
        SourceInventoryDiff unique = new SourceInventoryDiffer().diff(before, after);
        assertEquals(1, count(unique, SourceInventoryDiff.ChangeKind.MOVED));
        assertFalse(unique.ambiguousMoves());

        SourceInventory ambiguousBefore = inventory(projectId, "r3", T0,
                entry("a.md", "duplicate"), entry("b.md", "duplicate"));
        SourceInventory ambiguousAfter = inventory(projectId, "r4", T0.plusSeconds(1),
                entry("c.md", "duplicate"), entry("d.md", "duplicate"));
        SourceInventoryDiff ambiguous = new SourceInventoryDiffer().diff(ambiguousBefore, ambiguousAfter);
        assertTrue(ambiguous.ambiguousMoves());
        assertEquals(0, count(ambiguous, SourceInventoryDiff.ChangeKind.MOVED));
        assertEquals(2, count(ambiguous, SourceInventoryDiff.ChangeKind.DELETED));
        assertEquals(2, count(ambiguous, SourceInventoryDiff.ChangeKind.ADDED));
    }

    @Test
    void firstSynchronizationRequiresFullRebuildThenEstablishesBaseline() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        FakeSyncStateStore store = new FakeSyncStateStore();
        IncrementalSyncService service = new IncrementalSyncService(store);
        SourceInventory current = inventory(projectId, "r1", T0, entry("a.md", "a"));

        SyncPlan plan = service.prepare(SourceInventoryScanResult.complete(current), SyncPlan.Trigger.startup(), T0.plusSeconds(1));
        assertEquals(SyncPlan.SyncMode.FULL_REBUILD, plan.mode());
        assertEquals(SyncPlan.FullRebuildReason.NO_BASELINE, plan.fullRebuildReason().orElseThrow());
        assertEquals(List.of(new SourcePath("a.md")), plan.invalidation().refreshRequired());

        service.complete(plan, T0.plusSeconds(2));
        assertEquals(current, store.findCurrentInventory(projectId).orElseThrow());
        assertEquals(SyncPlan.SyncMode.FULL_REBUILD,
                store.findSyncState(projectId).orElseThrow().lastSuccessfulMode().orElseThrow());
        assertTrue(store.findSyncState(projectId).orElseThrow().pendingFullRebuildReason().isEmpty());
    }

    @Test
    void incrementalPlanProducesPreciseInvalidationRefreshAndArchives() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        FakeSyncStateStore store = seededStore(inventory(projectId, "r1", T0,
                entry("move-old.md", "m"), entry("edit.md", "old"), entry("delete.md", "d")));
        IncrementalSyncService service = new IncrementalSyncService(store);
        SourceInventory current = inventory(projectId, "r2", T0.plusSeconds(10),
                entry("move-new.md", "m"), entry("edit.md", "new"), entry("add.md", "a"));

        SyncPlan plan = service.prepare(SourceInventoryScanResult.complete(current), SyncPlan.Trigger.manual(), T0.plusSeconds(11));
        assertEquals(SyncPlan.SyncMode.INCREMENTAL, plan.mode());
        assertEquals(List.of("delete.md", "edit.md", "move-old.md"),
                plan.invalidation().invalidated().stream().map(SourcePath::toString).toList());
        assertEquals(List.of("add.md", "edit.md", "move-new.md"),
                plan.invalidation().refreshRequired().stream().map(SourcePath::toString).toList());
        assertEquals(2, plan.archiveActions().size());

        service.complete(plan, T0.plusSeconds(12));
        assertEquals(2, store.listArchives(projectId).size());
        assertEquals(T0.plusSeconds(12), store.findSyncState(projectId).orElseThrow().lastObservedChangeAt().orElseThrow());
    }

    @Test
    void sameRevisionWithChangedInventoryForcesFullRebuild() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        FakeSyncStateStore store = seededStore(inventory(projectId, "same-revision", T0, entry("a.md", "old")));
        SyncPlan plan = new IncrementalSyncService(store).prepare(
                SourceInventoryScanResult.complete(inventory(projectId, "same-revision", T0.plusSeconds(1), entry("a.md", "new"))),
                SyncPlan.Trigger.manual(), T0.plusSeconds(2));
        assertEquals(SyncPlan.SyncMode.FULL_REBUILD, plan.mode());
        assertEquals(SyncPlan.FullRebuildReason.REVISION_INCONSISTENCY, plan.fullRebuildReason().orElseThrow());
    }

    @Test
    void losingPreviouslyAvailableRevisionForcesFullRebuild() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        FakeSyncStateStore store = seededStore(inventory(projectId, "r1", T0, entry("a.md", "same")));
        SyncPlan plan = new IncrementalSyncService(store).prepare(
                SourceInventoryScanResult.complete(new SourceInventory(
                        projectId, Optional.empty(), T0.plusSeconds(1), List.of(entry("a.md", "same")))),
                SyncPlan.Trigger.manual(), T0.plusSeconds(2));
        assertEquals(SyncPlan.FullRebuildReason.REVISION_SIGNAL_LOST, plan.fullRebuildReason().orElseThrow());
    }

    @Test
    void watcherOverflowIncompleteScanAndAmbiguousMoveForceFullRebuild() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceInventory baseline = inventory(projectId, "r1", T0,
                entry("a.md", "dup"), entry("b.md", "dup"));

        FakeSyncStateStore overflowStore = seededStore(baseline);
        SyncPlan overflow = new IncrementalSyncService(overflowStore).prepare(
                SourceInventoryScanResult.complete(inventory(projectId, "r2", T0.plusSeconds(1), entry("a.md", "dup"), entry("b.md", "dup"))),
                SyncPlan.Trigger.watcher(List.of(SourceWatchSignal.overflow())), T0.plusSeconds(2));
        assertEquals(SyncPlan.FullRebuildReason.WATCH_OVERFLOW, overflow.fullRebuildReason().orElseThrow());

        FakeSyncStateStore incompleteStore = seededStore(baseline);
        SyncPlan incomplete = new IncrementalSyncService(incompleteStore).prepare(
                SourceInventoryScanResult.incomplete(projectId, List.of(new SourceInventoryScanResult.Failure(
                        Optional.empty(),
                        SourceInventoryScanResult.Failure.Code.SOURCE_UNREADABLE,
                        "denied"))),
                SyncPlan.Trigger.manual(), T0.plusSeconds(2));
        assertEquals(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE, incomplete.fullRebuildReason().orElseThrow());

        FakeSyncStateStore ambiguousStore = seededStore(baseline);
        SyncPlan ambiguous = new IncrementalSyncService(ambiguousStore).prepare(
                SourceInventoryScanResult.complete(inventory(projectId, "r2", T0.plusSeconds(1),
                        entry("c.md", "dup"), entry("d.md", "dup"))),
                SyncPlan.Trigger.manual(), T0.plusSeconds(2));
        assertEquals(SyncPlan.FullRebuildReason.AMBIGUOUS_MOVE, ambiguous.fullRebuildReason().orElseThrow());
    }

    @Test
    void failedExecutionLeavesPendingRebuildAndNextAttemptCannotResumeIncrementally() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        FakeSyncStateStore store = seededStore(inventory(projectId, "r1", T0, entry("a.md", "old")));
        IncrementalSyncService service = new IncrementalSyncService(store);
        SourceInventory changed = inventory(projectId, "r2", T0.plusSeconds(1), entry("a.md", "new"));
        SyncPlan first = service.prepare(SourceInventoryScanResult.complete(changed), SyncPlan.Trigger.manual(), T0.plusSeconds(2));
        assertEquals(SyncPlan.SyncMode.INCREMENTAL, first.mode());
        service.fail(first, T0.plusSeconds(3));

        FakeSyncStateStore sameStore = store;
        SyncPlan retry = service.prepare(SourceInventoryScanResult.complete(changed), SyncPlan.Trigger.manual(), T0.plusSeconds(4));
        assertEquals(SyncPlan.SyncMode.FULL_REBUILD, retry.mode());
        assertEquals(SyncPlan.FullRebuildReason.PREVIOUS_REBUILD_PENDING, retry.fullRebuildReason().orElseThrow());
        assertEquals(SyncPlan.FullRebuildReason.PREVIOUS_REBUILD_PENDING,
                sameStore.findSyncState(projectId).orElseThrow().pendingFullRebuildReason().orElseThrow());
    }

    @Test
    void freshnessIsUnknownFreshStaleOrRebuildRequiredUsingExplicitNow() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        FakeSyncStateStore empty = new FakeSyncStateStore();
        SyncFreshnessService emptyService = new SyncFreshnessService(empty);
        assertEquals(SyncFreshness.State.UNKNOWN,
                emptyService.assess(projectId, T0, Duration.ofMinutes(5)).state());

        FakeSyncStateStore store = seededStore(inventory(projectId, "r1", T0, entry("a.md", "a")));
        SyncFreshnessService service = new SyncFreshnessService(store);
        assertEquals(SyncFreshness.State.FRESH,
                service.assess(projectId, T0.plusSeconds(60), Duration.ofMinutes(5)).state());
        assertEquals(SyncFreshness.State.STALE,
                service.assess(projectId, T0.plusSeconds(600), Duration.ofMinutes(5)).state());

        store.recordAttempt(projectId, T0.plusSeconds(700), Optional.of(SyncPlan.FullRebuildReason.WATCH_OVERFLOW));
        assertEquals(SyncFreshness.State.REBUILD_REQUIRED,
                service.assess(projectId, T0.plusSeconds(701), Duration.ofMinutes(5)).state());
    }

    @Test
    void watchSignalPolicyKeepsPathsStableAndOverflowDominates() {
        WatchSignalPolicy policy = new WatchSignalPolicy();
        List<SourceWatchSignal> signals = List.of(
                new SourceWatchSignal(SourceWatchSignal.Kind.MODIFY, Optional.of(new SourcePath("b.md"))),
                new SourceWatchSignal(SourceWatchSignal.Kind.CREATE, Optional.of(new SourcePath("a.md"))),
                new SourceWatchSignal(SourceWatchSignal.Kind.MODIFY, Optional.of(new SourcePath("b.md"))),
                SourceWatchSignal.overflow());
        assertTrue(policy.requiresFullRebuild(signals));
        assertEquals(List.of("a.md", "b.md"), policy.affectedPaths(signals).stream().map(SourcePath::toString).toList());
    }

    @Test
    void localWatcherEmitsRescanSignalForExistingNestedDirectory() throws Exception {
        Path watched = Files.createDirectories(tempDir.resolve("specs/nested"));
        try (LocalSourceWatcher watcher = new LocalSourceWatcher(tempDir, List.of(Path.of("specs")))) {
            Files.writeString(watched.resolve("created.md"), "content");
            List<SourceWatchSignal> signals = watcher.poll(Duration.ofSeconds(5));
            assertTrue(signals.stream().anyMatch(signal ->
                    signal.path().map(SourcePath::toString).orElse("").equals("specs/nested/created.md")));
        }
    }

    private long count(SourceInventoryDiff diff, SourceInventoryDiff.ChangeKind kind) {
        return diff.changes().stream().filter(change -> change.kind() == kind).count();
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

    private static FakeSyncStateStore seededStore(SourceInventory inventory) {
        FakeSyncStateStore store = new FakeSyncStateStore();
        store.commitSuccessfulSync(
                inventory,
                SyncPlan.SyncMode.FULL_REBUILD,
                inventory.capturedAt(),
                inventory.capturedAt(),
                Optional.empty(),
                List.of());
        return store;
    }

    private static final class FakeSyncStateStore implements SyncStateStore {
        private final Map<ProjectSpecificationId, ProjectSyncState> states = new HashMap<>();
        private final Map<ProjectSpecificationId, SourceInventory> inventories = new HashMap<>();
        private final Map<ProjectSpecificationId, List<SourceArchiveRecord>> archives = new HashMap<>();

        @Override
        public Optional<ProjectSyncState> findSyncState(ProjectSpecificationId projectId) {
            return Optional.ofNullable(states.get(projectId));
        }

        @Override
        public Optional<SourceInventory> findCurrentInventory(ProjectSpecificationId projectId) {
            return Optional.ofNullable(inventories.get(projectId));
        }

        @Override
        public List<SourceArchiveRecord> listArchives(ProjectSpecificationId projectId) {
            return archives.getOrDefault(projectId, List.of()).stream().sorted().toList();
        }

        @Override
        public void recordAttempt(
                ProjectSpecificationId projectId,
                Instant attemptedAt,
                Optional<SyncPlan.FullRebuildReason> pendingFullRebuildReason) {
            ProjectSyncState previous = states.getOrDefault(projectId, ProjectSyncState.empty(projectId));
            states.put(projectId, new ProjectSyncState(
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
            inventories.put(inventory.projectId(), inventory);
            states.put(inventory.projectId(), new ProjectSyncState(
                    inventory.projectId(),
                    Optional.of(attemptedAt),
                    Optional.of(completedAt),
                    lastObservedChangeAt,
                    inventory.sourceRevision(),
                    Optional.of(mode),
                    Optional.empty(),
                    inventory.entries().size()));
            List<SourceArchiveRecord> list = archives.computeIfAbsent(inventory.projectId(), ignored -> new ArrayList<>());
            newArchives.forEach(record -> {
                if (!list.contains(record)) {
                    list.add(record);
                }
            });
        }
    }
}
