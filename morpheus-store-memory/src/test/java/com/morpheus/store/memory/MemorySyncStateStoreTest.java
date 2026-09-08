package com.morpheus.store.memory;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.sync.ProjectSyncState;
import com.morpheus.application.sync.SourceArchiveRecord;
import com.morpheus.application.sync.SourceFingerprint;
import com.morpheus.application.sync.SourceInventory;
import com.morpheus.application.sync.SourcePath;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reference synchronization-state adapter, held to the same refusals as the durable one.
 *
 * <p>Most of MORPHEUS is tested against this adapter, so anything it accepts that SQLite refuses becomes a
 * behaviour the rest of the suite agrees on and production does not have. The guards that decide a later sync's
 * mode -- project existence, timestamp coherence, archive ownership -- are therefore asserted here directly.</p>
 */
class MemorySyncStateStoreTest {
    private static final Instant ATTEMPTED = Instant.parse("2026-09-03T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-09-03T10:00:05Z");

    private MemorySpecificationKnowledgeStore projects;
    private MemorySyncStateStore store;
    private ProjectSpecificationId projectId;

    @BeforeEach
    void setUp() {
        projects = new MemorySpecificationKnowledgeStore();
        store = new MemorySyncStateStore(projects);
        projectId = ProjectSpecificationId.generate();
        projects.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
    }

    @Test
    void anUnknownProjectHasNoStateRatherThanAnEmptyOne() {
        ProjectSpecificationId unknown = ProjectSpecificationId.generate();

        assertEquals(Optional.empty(), store.findSyncState(unknown));
        assertEquals(Optional.empty(), store.findCurrentInventory(unknown));
        assertEquals(List.of(), store.listArchives(unknown));
    }

    @Test
    void anUnknownProjectCannotAcquireSynchronizationState() {
        ProjectSpecificationId unknown = ProjectSpecificationId.generate();

        assertTrue(assertThrows(KnowledgeStoreException.class,
                () -> store.recordAttempt(unknown, ATTEMPTED, Optional.empty()))
                .getMessage().contains("project not found for synchronization state"));
    }

    @Test
    void anAttemptIsRecordedWithoutClaimingASuccessfulSync() {
        store.recordAttempt(projectId, ATTEMPTED, Optional.of(SyncPlan.FullRebuildReason.BASELINE_INCONSISTENT));

        ProjectSyncState state = store.findSyncState(projectId).orElseThrow();
        assertEquals(Optional.of(ATTEMPTED), state.lastAttemptAt());
        assertEquals(Optional.empty(), state.lastSuccessfulSyncAt());
        assertEquals(Optional.empty(), state.lastSuccessfulMode());
        assertEquals(Optional.of(SyncPlan.FullRebuildReason.BASELINE_INCONSISTENT),
                state.pendingFullRebuildReason());
        assertEquals(Optional.empty(), store.findCurrentInventory(projectId));
    }

    @Test
    void aSuccessfulSyncClearsThePendingRebuildReasonAndPublishesTheInventory() {
        store.recordAttempt(projectId, ATTEMPTED, Optional.of(SyncPlan.FullRebuildReason.NO_BASELINE));

        store.commitSuccessfulSync(
                inventory("revision-1", entry("spec/a.md", "alpha")),
                SyncPlan.SyncMode.FULL_REBUILD, ATTEMPTED, COMPLETED, Optional.of(COMPLETED), List.of());

        ProjectSyncState state = store.findSyncState(projectId).orElseThrow();
        assertEquals(Optional.empty(), state.pendingFullRebuildReason());
        assertEquals(Optional.of(SyncPlan.SyncMode.FULL_REBUILD), state.lastSuccessfulMode());
        assertEquals(Optional.of(COMPLETED), state.lastSuccessfulSyncAt());
        assertEquals(Optional.of(COMPLETED), state.lastObservedChangeAt());
        assertEquals(1, state.currentSourceCount());
        assertEquals(Optional.of("revision-1"), store.findCurrentInventory(projectId).orElseThrow().sourceRevision());
    }

    @Test
    void aCommittedInventoryReplacesThePreviousOneWhole() {
        store.commitSuccessfulSync(
                inventory("revision-1", entry("spec/a.md", "alpha"), entry("spec/b.md", "beta")),
                SyncPlan.SyncMode.FULL_REBUILD, ATTEMPTED, COMPLETED, Optional.empty(), List.of());

        store.commitSuccessfulSync(
                inventory("revision-2", entry("spec/b.md", "beta-2")),
                SyncPlan.SyncMode.INCREMENTAL, COMPLETED, COMPLETED.plusSeconds(5), Optional.empty(), List.of());

        SourceInventory current = store.findCurrentInventory(projectId).orElseThrow();
        assertEquals(1, current.entries().size());
        assertEquals(new SourcePath("spec/b.md"), current.entries().get(0).path());
    }

    @Test
    void archivesAccumulateInOrderAndAreNeverDuplicated() {
        SourceArchiveRecord deleted = new SourceArchiveRecord(
                projectId, entry("spec/gone.md", "gone"), COMPLETED,
                SourceArchiveRecord.ArchiveReason.DELETED, Optional.empty(), Optional.of("revision-1"));
        SourceArchiveRecord moved = new SourceArchiveRecord(
                projectId, entry("spec/old.md", "moved"), COMPLETED.plusSeconds(5),
                SourceArchiveRecord.ArchiveReason.MOVED, Optional.of(new SourcePath("spec/new.md")),
                Optional.of("revision-2"));

        store.commitSuccessfulSync(
                inventory("revision-1", entry("spec/a.md", "alpha")),
                SyncPlan.SyncMode.FULL_REBUILD, ATTEMPTED, COMPLETED, Optional.empty(), List.of(moved, deleted));
        store.commitSuccessfulSync(
                inventory("revision-2", entry("spec/a.md", "alpha")),
                SyncPlan.SyncMode.INCREMENTAL, COMPLETED, COMPLETED.plusSeconds(5),
                Optional.empty(), List.of(deleted));

        assertEquals(List.of(deleted, moved), store.listArchives(projectId),
                "archives are ordered evidence, and re-observing one must not duplicate it");
    }

    @Test
    void aCommitWithIncoherentTimestampsOrAForeignArchiveIsRefused() {
        SourceInventory inventory = inventory("revision-1", entry("spec/a.md", "alpha"));
        SourceArchiveRecord foreign = new SourceArchiveRecord(
                ProjectSpecificationId.generate(), entry("spec/gone.md", "gone"), COMPLETED,
                SourceArchiveRecord.ArchiveReason.DELETED, Optional.empty(), Optional.empty());

        assertTrue(assertThrows(IllegalArgumentException.class, () -> store.commitSuccessfulSync(
                inventory, SyncPlan.SyncMode.INCREMENTAL, COMPLETED, ATTEMPTED, Optional.empty(), List.of()))
                .getMessage().contains("completedAt must not be before attemptedAt"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> store.commitSuccessfulSync(
                inventory, SyncPlan.SyncMode.INCREMENTAL, ATTEMPTED, COMPLETED,
                Optional.of(COMPLETED.plusSeconds(1)), List.of()))
                .getMessage().contains("lastObservedChangeAt must not be after completedAt"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> store.commitSuccessfulSync(
                inventory, SyncPlan.SyncMode.INCREMENTAL, ATTEMPTED, COMPLETED, Optional.empty(), List.of(foreign)))
                .getMessage().contains("archive belongs to another project"));

        assertEquals(Optional.empty(), store.findCurrentInventory(projectId),
                "a refused commit must leave no inventory behind");
    }

    private SourceInventory inventory(String revision, SourceInventory.Entry... entries) {
        return new SourceInventory(projectId, Optional.of(revision), ATTEMPTED, List.of(entries));
    }

    private static SourceInventory.Entry entry(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new SourceInventory.Entry(new SourcePath(path), SourceFingerprint.ofBytes(bytes), bytes.length);
    }
}
