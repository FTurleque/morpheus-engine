package com.morpheus.application.query;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.temporal.TemporalState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementQueryServiceTest {
    private final PublishedSnapshotWorld world = new PublishedSnapshotWorld();
    private final RequirementQueryService service =
            new RequirementQueryService(world.snapshots(), world.requirementStore());

    @Test
    void aSearchTextIsNormalizedAndSplitIntoTermsOnAnyWhitespace() {
        RequirementSearchQuery query = new RequirementSearchQuery("  Login\tTIMEOUT  audit ");

        assertEquals("login\ttimeout  audit", query.text());
        assertEquals(List.of("login", "timeout", "audit"), query.terms());
        assertEquals("", RequirementSearchQuery.all().text());
        assertEquals(List.of(), RequirementSearchQuery.all().terms());
        assertEquals(List.of(), new RequirementSearchQuery("   ").terms());
        NullPointerException noText = assertThrows(NullPointerException.class, () -> new RequirementSearchQuery(null));
        assertEquals("text", noText.getMessage());
    }

    @Test
    void theActiveSearchKeepsTheCurrentRequirementsThatMatchEveryTermPageByPage() {
        Specification specification = world.specification("spec");
        RequirementVersionRecord first = world.requirement(specification.id(), "R-1", "The login has a timeout");
        world.requirement(specification.id(), "R-2", "The audit is kept");
        RequirementVersionRecord third = world.requirement(specification.id(), "R-3", "A login timeout is audited");
        world.requirementIn(world.active().id(), specification.id(), "R-4", "A proposed login timeout",
                TemporalState.PROPOSED);
        RequirementSearchQuery query = new RequirementSearchQuery("LOGIN timeout");

        RequirementSearchPage head = service.findActive(world.projectId(), query, new PageRequest(0, 1)).orElseThrow();
        RequirementSearchPage tail = service.findActive(world.projectId(), query, new PageRequest(1, 1)).orElseThrow();

        assertEquals(world.active(), head.snapshot());
        assertEquals(query, head.query());
        assertEquals(List.of(first), head.items());
        assertEquals(2, head.totalMatches());
        assertTrue(head.hasMore());
        assertEquals(List.of(third), tail.items());
        assertFalse(tail.hasMore());
        assertEquals(3, service.findActive(world.projectId(), RequirementSearchQuery.all(), PageRequest.first(10))
                .orElseThrow().totalMatches());
    }

    @Test
    void aProjectWithoutAnActiveSnapshotHasNoSearch() {
        assertTrue(service.findActive(new ProjectSpecificationId(world.next()), RequirementSearchQuery.all(),
                PageRequest.first(1)).isEmpty());
    }

    @Test
    void aSnapshotSearchReadsARetiredSnapshotAndRefusesAnUnpublishedOrUnknownOne() {
        Specification specification = world.specification("spec");
        KnowledgeSnapshotMetadata retired = world.snapshot(KnowledgeSnapshotState.RETIRED);
        RequirementVersionRecord old = world.requirementIn(retired.id(), specification.id(), "R-1", "old statement",
                TemporalState.CURRENT);
        world.requirement(specification.id(), "R-1", "new statement");
        KnowledgeSnapshotMetadata ready = world.snapshot(KnowledgeSnapshotState.READY);
        KnowledgeSnapshotId unknown = new KnowledgeSnapshotId(world.next());

        RequirementSearchPage page = service.findSnapshot(
                retired.id(), RequirementSearchQuery.all(), PageRequest.first(5));

        assertEquals(retired, page.snapshot());
        assertEquals(List.of(old), page.items());
        KnowledgeStoreException notPublished = assertThrows(KnowledgeStoreException.class,
                () -> service.findSnapshot(ready.id(), RequirementSearchQuery.all(), PageRequest.first(5)));
        assertEquals("requirement query requires an ACTIVE or RETIRED snapshot: " + ready.id() + " is READY",
                notPublished.getMessage());
        KnowledgeStoreException missing = assertThrows(KnowledgeStoreException.class,
                () -> service.findSnapshot(unknown, RequirementSearchQuery.all(), PageRequest.first(5)));
        assertEquals("unknown knowledge snapshot: " + unknown, missing.getMessage());
    }

    @Test
    void aSearchPageRefusesBoundsThatContradictItsTotal() {
        Specification specification = world.specification("spec");
        RequirementVersionRecord record = world.requirement(specification.id(), "R-1", "statement");
        RequirementSearchQuery all = RequirementSearchQuery.all();

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> new RequirementSearchPage(world.active(), all, List.of(), PageRequest.first(1), -1, false));
        assertEquals("totalMatches must be greater than or equal to zero", negative.getMessage());
        IllegalArgumentException overLimit = assertThrows(IllegalArgumentException.class,
                () -> new RequirementSearchPage(world.active(), all, List.of(record, record), PageRequest.first(1),
                        2, false));
        assertEquals("items must not exceed requested page limit", overLimit.getMessage());
        IllegalArgumentException falseMore = assertThrows(IllegalArgumentException.class,
                () -> new RequirementSearchPage(world.active(), all, List.of(record), PageRequest.first(1), 1, true));
        assertEquals("hasMore is inconsistent with page bounds and totalMatches", falseMore.getMessage());
        IllegalArgumentException hiddenMore = assertThrows(IllegalArgumentException.class,
                () -> new RequirementSearchPage(world.active(), all, List.of(record), PageRequest.first(1), 4, false));
        assertEquals("hasMore is inconsistent with page bounds and totalMatches", hiddenMore.getMessage());
    }
}
