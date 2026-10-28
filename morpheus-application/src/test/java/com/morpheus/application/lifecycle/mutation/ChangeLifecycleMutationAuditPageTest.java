package com.morpheus.application.lifecycle.mutation;

import com.morpheus.application.query.PageRequest;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeLifecycleMutationAuditPageTest {
    private static final ProjectSpecificationId PROJECT = ProjectSpecificationId.generate();
    private static final ChangeId CHANGE = ChangeId.generate();
    private static final List<ChangeLifecycleMutationAuditRecord> JOURNAL = IntStream.range(0, 5)
            .mapToObj(ChangeLifecycleMutationAuditPageTest::record)
            .toList();

    @Test
    void aSliceOfAnOrderedJournalSaysItsTotalAndWhetherMoreFollows() {
        ChangeLifecycleMutationAuditPage first = ChangeLifecycleMutationAuditPage.of(JOURNAL, new PageRequest(0, 2));
        ChangeLifecycleMutationAuditPage last = ChangeLifecycleMutationAuditPage.of(JOURNAL, new PageRequest(4, 2));
        ChangeLifecycleMutationAuditPage beyond = ChangeLifecycleMutationAuditPage.of(JOURNAL, new PageRequest(7, 2));

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
                () -> new ChangeLifecycleMutationAuditPage(List.of(), two, -1, false));
        IllegalArgumentException tooMany = assertThrows(IllegalArgumentException.class,
                () -> new ChangeLifecycleMutationAuditPage(JOURNAL.subList(0, 3), two, 5, true));
        IllegalArgumentException wrongMore = assertThrows(IllegalArgumentException.class,
                () -> new ChangeLifecycleMutationAuditPage(JOURNAL.subList(0, 2), two, 5, false));

        assertEquals("totalMatches must be >= 0", negative.getMessage());
        assertEquals("items must not exceed page limit", tooMany.getMessage());
        assertEquals("hasMore is inconsistent with page bounds", wrongMore.getMessage());
    }

    private static ChangeLifecycleMutationAuditRecord record(int revision) {
        return new ChangeLifecycleMutationAuditRecord(
                ChangeLifecycleMutationId.generate(),
                new ChangeLifecycleIdempotencyKey("key-" + revision),
                "fingerprint",
                PROJECT,
                CHANGE,
                ChangeLifecycleState.DRAFT,
                ChangeLifecycleState.PROPOSED,
                Optional.empty(),
                new ChangeLifecycleRevision(revision),
                new ChangeLifecycleRevision(revision + 1),
                "test",
                new ProviderId("fixture"),
                "allowed",
                Instant.parse("2026-10-28T10:00:00Z"));
    }
}
