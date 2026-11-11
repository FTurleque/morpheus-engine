package com.morpheus.application.history;

import com.morpheus.application.store.RequirementPage;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.SpecificationId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.morpheus.application.history.PublishedHistoryStores.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementSnapshotComparisonServiceTest {
    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final SpecificationId specificationId = SpecificationId.generate();
    private final PublishedHistoryStores stores = new PublishedHistoryStores();
    private final RequirementSnapshotComparisonService comparison =
            new RequirementSnapshotComparisonService(stores, stores);
    private final HistoricalRequirementQueryService history = new HistoricalRequirementQueryService(stores, stores);

    @Test
    void everyIdentityOfEitherProjectionIsClassifiedOnceInIdentityOrder() {
        KnowledgeSnapshotMetadata retired = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata active = stores.publish(
                projectId, Optional.of(retired.id()), KnowledgeSnapshotState.ACTIVE);
        stores.addRequirement(retired.id(), identity(4), specificationId, "Kept as is");
        stores.addRequirement(active.id(), identity(4), specificationId, "Kept as is");
        stores.addRequirement(retired.id(), identity(3), specificationId, "Dropped later");
        stores.addRequirement(retired.id(), identity(1), specificationId, "Old wording");
        stores.addRequirement(active.id(), identity(1), specificationId, "New wording");
        stores.addRequirement(active.id(), identity(2), specificationId, "Introduced later");

        RequirementSnapshotComparison result = comparison.compare(retired.id(), active.id());

        assertEquals(retired, result.sourceSnapshot());
        assertEquals(active, result.targetSnapshot());
        assertEquals(List.of(identity(1), identity(2), identity(3), identity(4)),
                result.differences().stream().map(RequirementSnapshotDifference::entityIdentity).toList());
        assertEquals(List.of(
                        RequirementSnapshotChangeKind.MODIFIED,
                        RequirementSnapshotChangeKind.ADDED,
                        RequirementSnapshotChangeKind.REMOVED,
                        RequirementSnapshotChangeKind.UNCHANGED),
                result.differences().stream().map(RequirementSnapshotDifference::kind).toList());
        RequirementSnapshotDifference modified = result.differences().getFirst();
        assertEquals("Old wording", modified.source().orElseThrow().entityVersion().content().statement());
        assertEquals("New wording", modified.target().orElseThrow().entityVersion().content().statement());
        assertTrue(result.differences().get(1).source().isEmpty());
        assertTrue(result.differences().get(2).target().isEmpty());
    }

    @Test
    void anUnknownOrUnpublishedOrUnboundSnapshotIsRefusedByName() {
        KnowledgeSnapshotMetadata active = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.ACTIVE);
        KnowledgeSnapshotMetadata ready = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.READY);
        KnowledgeSnapshotMetadata unbound = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.RETIRED);
        stores.unbind(unbound.id());
        KnowledgeSnapshotId unknown = KnowledgeSnapshotId.generate();

        PublishedHistoryException missing = assertThrows(PublishedHistoryException.class,
                () -> comparison.compare(unknown, active.id()));
        assertEquals("snapshot not found: " + unknown, missing.getMessage());
        PublishedHistoryException unpublished = assertThrows(PublishedHistoryException.class,
                () -> comparison.compare(active.id(), ready.id()));
        assertEquals("snapshot comparison requires published snapshots but was READY: " + ready.id(),
                unpublished.getMessage());
        PublishedHistoryException noBinding = assertThrows(PublishedHistoryException.class,
                () -> comparison.compare(unbound.id(), active.id()));
        assertEquals("published snapshot has no specification version binding: " + unbound.id(),
                noBinding.getMessage());
    }

    @Test
    void twoSnapshotsOfDifferentProjectsAreNeverCompared() {
        KnowledgeSnapshotMetadata mine = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.ACTIVE);
        KnowledgeSnapshotMetadata theirs = stores.publish(
                ProjectSpecificationId.generate(), Optional.empty(), KnowledgeSnapshotState.ACTIVE);

        PublishedHistoryException failure = assertThrows(PublishedHistoryException.class,
                () -> comparison.compare(mine.id(), theirs.id()));

        assertEquals("snapshot comparison cannot cross project boundaries", failure.getMessage());
        IllegalArgumentException record = assertThrows(IllegalArgumentException.class,
                () -> new RequirementSnapshotComparison(mine, theirs, List.of()));
        assertEquals("snapshot comparison cannot cross project boundaries", record.getMessage());
    }

    @Test
    void twoCurrentOccurrencesOfOneIdentityAreRefusedInsteadOfKeepingTheLastOne() {
        KnowledgeSnapshotMetadata retired = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata active = stores.publish(
                projectId, Optional.of(retired.id()), KnowledgeSnapshotState.ACTIVE);
        stores.addRequirement(active.id(), identity(5), specificationId, "First");
        stores.addRequirement(active.id(), identity(5), specificationId, "Second");

        PublishedHistoryException failure = assertThrows(PublishedHistoryException.class,
                () -> comparison.compare(retired.id(), active.id()));

        assertEquals("published snapshot contains multiple CURRENT occurrences for " + identity(5),
                failure.getMessage());
    }

    @Test
    void aDifferenceMustCarryTheOccurrencesItsKindNamesAndOnlyOfItsIdentity() {
        KnowledgeSnapshotMetadata active = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.ACTIVE);
        RequirementVersionRecord record = stores.addRequirement(active.id(), identity(6), specificationId, "Text");
        Optional<RequirementVersionRecord> present = Optional.of(record);

        assertEquals("ADDED requires absent source and present target", assertThrows(IllegalArgumentException.class,
                () -> difference(RequirementSnapshotChangeKind.ADDED, present, present)).getMessage());
        assertEquals("REMOVED requires present source and absent target", assertThrows(IllegalArgumentException.class,
                () -> difference(RequirementSnapshotChangeKind.REMOVED, Optional.empty(), present)).getMessage());
        assertEquals("MODIFIED requires source and target occurrences", assertThrows(IllegalArgumentException.class,
                () -> difference(RequirementSnapshotChangeKind.MODIFIED, present, Optional.empty())).getMessage());
        assertEquals("target occurrence identity does not match difference identity",
                assertThrows(IllegalArgumentException.class, () -> new RequirementSnapshotDifference(
                        identity(7), RequirementSnapshotChangeKind.ADDED, Optional.empty(), present)).getMessage());
        assertEquals(RequirementSnapshotChangeKind.UNCHANGED,
                difference(RequirementSnapshotChangeKind.UNCHANGED, present, present).kind());
    }

    @Test
    void theHistoricalQueryPagesAndLooksUpTheCurrentRequirementsOfARetiredSnapshot() {
        KnowledgeSnapshotMetadata retired = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.RETIRED);
        stores.publish(projectId, Optional.of(retired.id()), KnowledgeSnapshotState.ACTIVE);
        stores.addRequirement(retired.id(), identity(9), specificationId, "Ninth");
        stores.addRequirement(retired.id(), identity(8), specificationId, "Eighth");

        RequirementPage page = history.requirements(retired.id(), 0, 1);

        assertEquals(2, page.totalMatches());
        assertEquals(1, page.items().size());
        assertEquals("Ninth", history.requirement(retired.id(), identity(9)).orElseThrow()
                .entityVersion().content().statement());
        assertTrue(history.requirement(retired.id(), DomainIdentity.generate()).isEmpty());
    }

    @Test
    void theHistoricalQueryRefusesWhatWasNeverPublished() {
        KnowledgeSnapshotMetadata building = stores.publish(
                projectId, Optional.empty(), KnowledgeSnapshotState.BUILDING);
        KnowledgeSnapshotMetadata unbound = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.ACTIVE);
        stores.unbind(unbound.id());
        KnowledgeSnapshotId unknown = KnowledgeSnapshotId.generate();

        assertEquals("snapshot not found: " + unknown, assertThrows(PublishedHistoryException.class,
                () -> history.requirements(unknown, 0, 10)).getMessage());
        assertEquals("historical query requires ACTIVE or RETIRED snapshot but was BUILDING: " + building.id(),
                assertThrows(PublishedHistoryException.class,
                        () -> history.requirement(building.id(), identity(1))).getMessage());
        assertEquals("published snapshot has no specification version binding: " + unbound.id(),
                assertThrows(PublishedHistoryException.class,
                        () -> history.requirements(unbound.id(), 0, 10)).getMessage());
    }

    private static RequirementSnapshotDifference difference(
            RequirementSnapshotChangeKind kind,
            Optional<RequirementVersionRecord> source,
            Optional<RequirementVersionRecord> target) {
        return new RequirementSnapshotDifference(identity(6), kind, source, target);
    }
}
