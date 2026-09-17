package com.morpheus.store.sqlite;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Durable synchronization state: what an interrupted sync leaves behind, and what it refuses to claim.
 *
 * <p>This is the state a later run reads to decide between an incremental pass and a full rebuild, so the
 * failures that matter are the quiet ones: an attempt that records itself as a success, a pending rebuild reason
 * that disappears across a reopen, or an archive attributed to the wrong project. Each is asserted against a real
 * database file, because losing an optional column is exactly how the first two would happen.</p>
 */
class SqliteSyncStateStorePersistenceTest {
    private static final Instant ATTEMPTED = Instant.parse("2026-09-03T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-09-03T10:00:05Z");

    @TempDir
    Path tempDir;

    @Test
    void anUnknownProjectHasNoStateRatherThanAnEmptyOne() {
        Path database = tempDir.resolve("unknown.db");

        try (SqliteSyncStateStore store = new SqliteSyncStateStore(database)) {
            ProjectSpecificationId unknown = ProjectSpecificationId.generate();

            assertEquals(Optional.empty(), store.findSyncState(unknown));
            assertEquals(Optional.empty(), store.findCurrentInventory(unknown));
            assertEquals(List.of(), store.listArchives(unknown));
        }
    }

    @Test
    void anAttemptIsRecordedWithoutClaimingASuccessfulSync() {
        Path database = tempDir.resolve("attempt.db");
        ProjectSpecificationId projectId = project(database);

        try (SqliteSyncStateStore store = new SqliteSyncStateStore(database)) {
            store.recordAttempt(projectId, ATTEMPTED, Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE));

            ProjectSyncState state = store.findSyncState(projectId).orElseThrow();
            assertEquals(Optional.of(ATTEMPTED), state.lastAttemptAt());
            assertEquals(Optional.empty(), state.lastSuccessfulSyncAt());
            assertEquals(Optional.empty(), state.lastSuccessfulMode());
            assertEquals(Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE), state.pendingFullRebuildReason());
            assertEquals(0, state.currentSourceCount());
        }
    }

    @Test
    void aPendingFullRebuildReasonSurvivesAReopenAndIsClearedBySuccess() {
        Path database = tempDir.resolve("pending.db");
        ProjectSpecificationId projectId = project(database);

        try (SqliteSyncStateStore store = new SqliteSyncStateStore(database)) {
            store.recordAttempt(projectId, ATTEMPTED, Optional.of(SyncPlan.FullRebuildReason.WATCH_OVERFLOW));
        }

        try (SqliteSyncStateStore reopened = new SqliteSyncStateStore(database)) {
            assertEquals(Optional.of(SyncPlan.FullRebuildReason.WATCH_OVERFLOW),
                    reopened.findSyncState(projectId).orElseThrow().pendingFullRebuildReason());

            reopened.commitSuccessfulSync(
                    inventory(projectId, "revision-1", entry("spec/a.md", "alpha")),
                    SyncPlan.SyncMode.FULL_REBUILD,
                    ATTEMPTED,
                    COMPLETED,
                    Optional.of(COMPLETED),
                    List.of());

            ProjectSyncState state = reopened.findSyncState(projectId).orElseThrow();
            assertEquals(Optional.empty(), state.pendingFullRebuildReason(),
                    "a completed rebuild must clear the reason that demanded it");
            assertEquals(Optional.of(SyncPlan.SyncMode.FULL_REBUILD), state.lastSuccessfulMode());
            assertEquals(Optional.of(COMPLETED), state.lastSuccessfulSyncAt());
            assertEquals(Optional.of("revision-1"), state.sourceRevision());
            assertEquals(1, state.currentSourceCount());
        }
    }

    @Test
    void aCommittedInventoryReplacesThePreviousOneWholeAndSurvivesAReopen() {
        Path database = tempDir.resolve("inventory.db");
        ProjectSpecificationId projectId = project(database);

        try (SqliteSyncStateStore store = new SqliteSyncStateStore(database)) {
            store.commitSuccessfulSync(
                    inventory(projectId, "revision-1", entry("spec/a.md", "alpha"), entry("spec/b.md", "beta")),
                    SyncPlan.SyncMode.FULL_REBUILD, ATTEMPTED, COMPLETED, Optional.empty(), List.of());
            store.commitSuccessfulSync(
                    inventory(projectId, "revision-2", entry("spec/b.md", "beta-2")),
                    SyncPlan.SyncMode.INCREMENTAL, COMPLETED, COMPLETED.plusSeconds(5), Optional.empty(), List.of());
        }

        try (SqliteSyncStateStore reopened = new SqliteSyncStateStore(database)) {
            SourceInventory current = reopened.findCurrentInventory(projectId).orElseThrow();
            assertEquals(1, current.entries().size(), "a replaced inventory must not leave its predecessor behind");
            assertEquals(new SourcePath("spec/b.md"), current.entries().get(0).path());
            assertEquals(Optional.of("revision-2"), current.sourceRevision());
            assertEquals(SourceFingerprint.ofBytes("beta-2".getBytes(StandardCharsets.UTF_8)),
                    current.entries().get(0).fingerprint());
        }
    }

    @Test
    void anInventoryWithoutARevisionStaysWithoutOne() {
        Path database = tempDir.resolve("no-revision.db");
        ProjectSpecificationId projectId = project(database);

        try (SqliteSyncStateStore store = new SqliteSyncStateStore(database)) {
            store.commitSuccessfulSync(
                    new SourceInventory(projectId, Optional.empty(), ATTEMPTED, List.of(entry("spec/a.md", "alpha"))),
                    SyncPlan.SyncMode.FULL_REBUILD, ATTEMPTED, COMPLETED, Optional.empty(), List.of());

            assertEquals(Optional.empty(), store.findCurrentInventory(projectId).orElseThrow().sourceRevision());
            assertEquals(Optional.empty(), store.findSyncState(projectId).orElseThrow().sourceRevision());
        }
    }

    @Test
    void archivesAreImmutableEvidenceThatAccumulatesAcrossSyncs() {
        Path database = tempDir.resolve("archives.db");
        ProjectSpecificationId projectId = project(database);
        SourceArchiveRecord deleted = new SourceArchiveRecord(
                projectId, entry("spec/gone.md", "gone"), COMPLETED,
                SourceArchiveRecord.ArchiveReason.DELETED, Optional.empty(), Optional.of("revision-1"));
        SourceArchiveRecord moved = new SourceArchiveRecord(
                projectId, entry("spec/old.md", "moved"), COMPLETED.plusSeconds(5),
                SourceArchiveRecord.ArchiveReason.MOVED, Optional.of(new SourcePath("spec/new.md")),
                Optional.of("revision-2"));

        try (SqliteSyncStateStore store = new SqliteSyncStateStore(database)) {
            store.commitSuccessfulSync(
                    inventory(projectId, "revision-1", entry("spec/a.md", "alpha")),
                    SyncPlan.SyncMode.FULL_REBUILD, ATTEMPTED, COMPLETED, Optional.empty(), List.of(deleted));
            store.commitSuccessfulSync(
                    inventory(projectId, "revision-2", entry("spec/a.md", "alpha")),
                    SyncPlan.SyncMode.INCREMENTAL, COMPLETED, COMPLETED.plusSeconds(5),
                    Optional.empty(), List.of(moved));
        }

        try (SqliteSyncStateStore reopened = new SqliteSyncStateStore(database)) {
            List<SourceArchiveRecord> archives = reopened.listArchives(projectId);
            assertEquals(List.of(deleted, moved), archives,
                    "archives accumulate in observation order and are never replaced");
            assertEquals(Optional.of(new SourcePath("spec/new.md")), archives.get(1).movedTo());
        }
    }

    @Test
    void anArchiveBelongingToAnotherProjectIsRefused() {
        Path database = tempDir.resolve("foreign-archive.db");
        ProjectSpecificationId projectId = project(database);
        ProjectSpecificationId other = ProjectSpecificationId.generate();

        try (SqliteSyncStateStore store = new SqliteSyncStateStore(database)) {
            SourceArchiveRecord foreign = new SourceArchiveRecord(
                    other, entry("spec/gone.md", "gone"), COMPLETED,
                    SourceArchiveRecord.ArchiveReason.DELETED, Optional.empty(), Optional.empty());

            assertTrue(assertThrows(IllegalArgumentException.class, () -> store.commitSuccessfulSync(
                    inventory(projectId, "revision-1", entry("spec/a.md", "alpha")),
                    SyncPlan.SyncMode.FULL_REBUILD, ATTEMPTED, COMPLETED, Optional.empty(), List.of(foreign)))
                    .getMessage().contains("archive belongs to another project"));
            assertEquals(Optional.empty(), store.findCurrentInventory(projectId),
                    "a refused commit must not have written its inventory either");
        }
    }

    @Test
    void aCommitWithIncoherentTimestampsIsRefused() {
        Path database = tempDir.resolve("timestamps.db");
        ProjectSpecificationId projectId = project(database);

        try (SqliteSyncStateStore store = new SqliteSyncStateStore(database)) {
            SourceInventory inventory = inventory(projectId, "revision-1", entry("spec/a.md", "alpha"));

            assertTrue(assertThrows(IllegalArgumentException.class, () -> store.commitSuccessfulSync(
                    inventory, SyncPlan.SyncMode.INCREMENTAL, COMPLETED, ATTEMPTED, Optional.empty(), List.of()))
                    .getMessage().contains("completedAt must not be before attemptedAt"));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> store.commitSuccessfulSync(
                    inventory, SyncPlan.SyncMode.INCREMENTAL, ATTEMPTED, COMPLETED,
                    Optional.of(COMPLETED.plusSeconds(1)), List.of()))
                    .getMessage().contains("lastObservedChangeAt must not be after completedAt"));
        }
    }

    @Test
    void anUnknownProjectCannotAcquireSynchronizationState() {
        Path database = tempDir.resolve("orphan.db");

        try (SqliteSyncStateStore store = new SqliteSyncStateStore(database)) {
            ProjectSpecificationId orphan = ProjectSpecificationId.generate();

            assertTrue(assertThrows(KnowledgeStoreException.class,
                    () -> store.recordAttempt(orphan, ATTEMPTED, Optional.empty()))
                    .getMessage().contains("project not found for synchronization state"));
        }
    }

    @Test
    void aClosedStoreRefusesEveryOperationInsteadOfFailingLater() {
        Path database = tempDir.resolve("closed.db");
        ProjectSpecificationId projectId = project(database);
        SqliteSyncStateStore store = new SqliteSyncStateStore(database);
        store.close();
        store.close();

        assertThrows(KnowledgeStoreException.class, () -> store.findSyncState(projectId));
        assertThrows(KnowledgeStoreException.class, () -> store.findCurrentInventory(projectId));
        assertThrows(KnowledgeStoreException.class, () -> store.listArchives(projectId));
        assertThrows(KnowledgeStoreException.class, () -> store.recordAttempt(projectId, ATTEMPTED, Optional.empty()));
    }

    private ProjectSpecificationId project(Path database) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        try (SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(database)) {
            store.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        }
        return projectId;
    }

    private static SourceInventory inventory(
            ProjectSpecificationId projectId, String revision, SourceInventory.Entry... entries) {
        return new SourceInventory(projectId, Optional.of(revision), ATTEMPTED, List.of(entries));
    }

    private static SourceInventory.Entry entry(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new SourceInventory.Entry(
                new SourcePath(path), SourceFingerprint.ofBytes(bytes), bytes.length);
    }
}
