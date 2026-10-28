package com.morpheus.architecture;

import com.morpheus.application.query.PageRequest;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SyncStateStore;
import com.morpheus.application.sync.ProjectSyncState;
import com.morpheus.application.sync.SourceArchivePage;
import com.morpheus.application.sync.SourceArchiveRecord.ArchiveReason;
import com.morpheus.application.sync.SourceArchiveRecord;
import com.morpheus.application.sync.SourceFingerprint;
import com.morpheus.application.sync.SourceInventory;
import com.morpheus.application.sync.SourcePath;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemorySyncStateStore;
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteSyncStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The source archives of a project come back by instant, then path, then reason, then fingerprint, on both stores, one
 * page at a time. The instant is stored as text whose order is not the order of time, so the SQLite store orders by a
 * fixed-width key of the instant; the pages it cuts in its query are the pages the memory store cuts from its list.
 */
class SourceArchiveOrderParityTest {
    private static final Instant ATTEMPTED = Instant.parse("2026-10-17T10:00:00Z");
    private static final Instant WHOLE_SECOND = Instant.parse("2026-10-17T10:00:00Z");
    private static final Instant HALF_SECOND = Instant.parse("2026-10-17T10:00:00.500Z");
    private static final Instant HALF_SECOND_AND_A_BIT = Instant.parse("2026-10-17T10:00:00.500100Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryStoreReturnsTheArchivesByInstantThenPathThenReason() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var core = new MemorySpecificationKnowledgeStore();
        core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));

        verifyOrder(new MemorySyncStateStore(core), projectId);
    }

    @Test
    void sqliteStoreReturnsTheArchivesByInstantThenPathThenReason() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        Path database = tempDir.resolve("archive-order.db");
        try (var core = new SqliteSpecificationKnowledgeStore(database);
             var store = new SqliteSyncStateStore(database)) {
            core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));

            verifyOrder(store, projectId);
        }
    }

    @Test
    void bothStoresCutTheSamePagesAcrossInstantsWhoseTextSortsAgainstTime() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var core = new MemorySpecificationKnowledgeStore();
        core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        List<List<SourceArchiveRecord>> memoryPages = pagesOfTwo(new MemorySyncStateStore(core), projectId);

        Path database = tempDir.resolve("archive-pages.db");
        List<List<SourceArchiveRecord>> sqlitePages;
        try (var sqliteCore = new SqliteSpecificationKnowledgeStore(database);
             var store = new SqliteSyncStateStore(database)) {
            sqliteCore.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
            sqlitePages = pagesOfTwo(store, projectId);
        }

        List<SourceArchiveRecord> concatenated = memoryPages.stream().flatMap(List::stream).toList();
        assertEquals(concatenated.stream().sorted().toList(), concatenated,
                "the pages put end to end are the archives in the order SourceArchiveRecord compares them");
        assertEquals(7, concatenated.size(), "no archive is read twice or skipped at a page boundary");
        assertEquals(memoryPages, sqlitePages, "the SQLite store cuts the pages the memory store cuts");
    }

    private List<List<SourceArchiveRecord>> pagesOfTwo(SyncStateStore store, ProjectSpecificationId projectId) {
        SourceArchiveRecord wholeSecond = archive(projectId, "spec/c.md", WHOLE_SECOND, ArchiveReason.DELETED);
        SourceArchiveRecord halfSecond = archive(projectId, "spec/a.md", HALF_SECOND, ArchiveReason.DELETED);
        SourceArchiveRecord halfSecondAndABit =
                archive(projectId, "spec/b.md", HALF_SECOND_AND_A_BIT, ArchiveReason.DELETED);
        SourceArchiveRecord nanosecond = archive(projectId, "spec/d.md", WHOLE_SECOND.plusNanos(1), ArchiveReason.DELETED);
        SourceArchiveRecord sharedDeleted = archive(projectId, "spec/z.md", WHOLE_SECOND.plusSeconds(1), ArchiveReason.DELETED);
        SourceArchiveRecord sharedOtherContent = new SourceArchiveRecord(
                projectId, entry("spec/z.md", "another content"), WHOLE_SECOND.plusSeconds(1), ArchiveReason.DELETED,
                Optional.empty(), Optional.of("revision-0"));
        SourceArchiveRecord sharedMoved = movedArchive(projectId, "spec/z.md", WHOLE_SECOND.plusSeconds(1));

        store.commitSuccessfulSync(
                new SourceInventory(projectId, Optional.of("revision-1"), ATTEMPTED, List.of(entry("spec/kept.md"))),
                0L, SyncPlan.SyncMode.FULL_REBUILD, ATTEMPTED, ATTEMPTED.plusSeconds(5), Optional.empty(),
                List.of(sharedMoved, sharedOtherContent, halfSecondAndABit, sharedDeleted, nanosecond, wholeSecond,
                        halfSecond));

        List<List<SourceArchiveRecord>> pages = new ArrayList<>();
        for (int offset = 0; offset < 9; offset += 2) {
            SourceArchivePage page = store.listArchives(projectId, new PageRequest(offset, 2));
            assertEquals(7, page.totalMatches());
            pages.add(page.items());
        }
        assertEquals(List.of(), pages.getLast(), "a page past the end is empty, not an error");
        return pages;
    }

    private void verifyOrder(SyncStateStore store, ProjectSpecificationId projectId) {
        SourceArchiveRecord wholeSecond = archive(projectId, "spec/c.md", WHOLE_SECOND, ArchiveReason.DELETED);
        SourceArchiveRecord halfSecond = archive(projectId, "spec/a.md", HALF_SECOND, ArchiveReason.DELETED);
        SourceArchiveRecord halfSecondAndABit =
                archive(projectId, "spec/b.md", HALF_SECOND_AND_A_BIT, ArchiveReason.DELETED);
        SourceArchiveRecord sharedDeleted = archive(projectId, "spec/z.md", WHOLE_SECOND.plusSeconds(1), ArchiveReason.DELETED);
        SourceArchiveRecord sharedMoved = movedArchive(projectId, "spec/z.md", WHOLE_SECOND.plusSeconds(1));
        SourceArchiveRecord sharedEarlierPath = archive(projectId, "spec/y.md", WHOLE_SECOND.plusSeconds(1), ArchiveReason.DELETED);

        store.commitSuccessfulSync(
                new SourceInventory(projectId, Optional.of("revision-1"), ATTEMPTED, List.of(entry("spec/kept.md"))),
                store.findSyncState(projectId).map(ProjectSyncState::revision).orElse(0L),
                SyncPlan.SyncMode.FULL_REBUILD, ATTEMPTED, ATTEMPTED.plusSeconds(5), Optional.empty(),
                List.of(sharedMoved, halfSecondAndABit, sharedDeleted, wholeSecond, sharedEarlierPath, halfSecond));

        List<SourceArchiveRecord> expected = List.of(
                wholeSecond, halfSecond, halfSecondAndABit, sharedEarlierPath, sharedDeleted, sharedMoved);
        assertEquals(expected, JournalPages.allArchives(store, projectId));
        assertEquals(expected, JournalPages.allArchives(store, projectId), "the same order on every read");
    }

    private static SourceArchiveRecord archive(
            ProjectSpecificationId projectId, String path, Instant archivedAt, ArchiveReason reason) {
        return new SourceArchiveRecord(
                projectId, entry(path), archivedAt, reason, Optional.empty(), Optional.of("revision-0"));
    }

    private static SourceArchiveRecord movedArchive(ProjectSpecificationId projectId, String path, Instant archivedAt) {
        return new SourceArchiveRecord(
                projectId, entry(path), archivedAt, ArchiveReason.MOVED,
                Optional.of(new SourcePath("spec/moved-to.md")), Optional.of("revision-0"));
    }

    private static SourceInventory.Entry entry(String path) {
        return entry(path, path);
    }

    private static SourceInventory.Entry entry(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new SourceInventory.Entry(new SourcePath(path), SourceFingerprint.ofBytes(bytes), bytes.length);
    }
}
