package com.morpheus.application.sync;

import com.morpheus.application.query.PageRequest;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceArchivePageTest {
    private static final ProjectSpecificationId PROJECT = ProjectSpecificationId.generate();
    private static final List<SourceArchiveRecord> JOURNAL = IntStream.range(0, 5)
            .mapToObj(SourceArchivePageTest::archive)
            .toList();

    @Test
    void aSliceOfAnOrderedJournalSaysItsTotalAndWhetherMoreFollows() {
        SourceArchivePage first = SourceArchivePage.of(JOURNAL, new PageRequest(0, 2));
        SourceArchivePage last = SourceArchivePage.of(JOURNAL, new PageRequest(4, 2));
        SourceArchivePage beyond = SourceArchivePage.of(JOURNAL, new PageRequest(7, 2));

        assertEquals(JOURNAL.subList(0, 2), first.items());
        assertTrue(first.hasMore());
        assertEquals(JOURNAL.subList(4, 5), last.items());
        assertFalse(last.hasMore());
        assertEquals(List.of(), beyond.items());
        assertEquals(5, beyond.totalMatches());
    }

    @Test
    void aPageInconsistentWithItsBoundsIsRefused() {
        PageRequest two = new PageRequest(0, 2);

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> new SourceArchivePage(List.of(), two, -1, false));
        IllegalArgumentException tooMany = assertThrows(IllegalArgumentException.class,
                () -> new SourceArchivePage(JOURNAL.subList(0, 3), two, 5, true));
        IllegalArgumentException wrongMore = assertThrows(IllegalArgumentException.class,
                () -> new SourceArchivePage(JOURNAL.subList(0, 2), two, 5, false));

        assertEquals("totalMatches must be >= 0", negative.getMessage());
        assertEquals("items must not exceed page limit", tooMany.getMessage());
        assertEquals("hasMore is inconsistent with page bounds", wrongMore.getMessage());
    }

    @Test
    void archivesThatDifferOnlyByContentAreOrderedByFingerprint() {
        SourceArchiveRecord one = archive("spec/a.md", "1".repeat(64));
        SourceArchiveRecord other = archive("spec/a.md", "f".repeat(64));

        assertEquals(List.of(one, other), List.of(other, one).stream().sorted().toList());
        assertTrue(one.compareTo(other) < 0);
    }

    private static SourceArchiveRecord archive(int index) {
        return archive("spec/" + index + ".md", String.valueOf(index).repeat(64));
    }

    private static SourceArchiveRecord archive(String path, String sha256) {
        return new SourceArchiveRecord(
                PROJECT,
                new SourceInventory.Entry(new SourcePath(path), new SourceFingerprint(sha256), 1),
                Instant.parse("2026-10-28T10:00:00Z"),
                SourceArchiveRecord.ArchiveReason.DELETED,
                Optional.empty(),
                Optional.empty());
    }
}
