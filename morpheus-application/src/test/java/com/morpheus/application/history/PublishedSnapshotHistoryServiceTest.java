package com.morpheus.application.history;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishedSnapshotHistoryServiceTest {
    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final PublishedHistoryStores stores = new PublishedHistoryStores();
    private final PublishedSnapshotHistoryService service = new PublishedSnapshotHistoryService(stores);

    @Test
    void aProjectWithoutAnActiveSnapshotHasNoLineageAndNothingPublishedIsEverPruned() {
        assertTrue(service.lineage(projectId).isEmpty());
        assertEquals(PublishedHistoryRetentionPolicy.KEEP_ALL_PUBLISHED, service.retentionPolicy());
    }

    @Test
    void theLineageRunsFromTheOldestRetiredPredecessorToTheActiveHead() {
        KnowledgeSnapshotMetadata first = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata second = stores.publish(
                projectId, Optional.of(first.id()), KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata head = stores.publish(
                projectId, Optional.of(second.id()), KnowledgeSnapshotState.ACTIVE);

        assertEquals(List.of(first, second, head), service.lineage(projectId));
    }

    @Test
    void aHeadThatIsNotActiveIsRefusedByName() {
        KnowledgeSnapshotMetadata ready = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.READY);
        stores.pointActiveAt(projectId, ready.id());

        PublishedHistoryException failure = assertThrows(PublishedHistoryException.class,
                () -> service.lineage(projectId));

        assertEquals("published lineage head must be ACTIVE: " + ready.id(), failure.getMessage());
    }

    @Test
    void aPredecessorThatIsNotRetiredIsRefusedWithItsState() {
        KnowledgeSnapshotMetadata failed = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.FAILED);
        stores.publish(projectId, Optional.of(failed.id()), KnowledgeSnapshotState.ACTIVE);

        PublishedHistoryException failure = assertThrows(PublishedHistoryException.class,
                () -> service.lineage(projectId));

        assertEquals("published predecessor must be RETIRED but was FAILED: " + failed.id(), failure.getMessage());
    }

    @Test
    void aMissingPredecessorIsRefusedRatherThanTruncatingTheLineage() {
        KnowledgeSnapshotId unknown = KnowledgeSnapshotId.generate();
        stores.publish(projectId, Optional.of(unknown), KnowledgeSnapshotState.ACTIVE);

        PublishedHistoryException failure = assertThrows(PublishedHistoryException.class,
                () -> service.lineage(projectId));

        assertEquals("published predecessor not found: " + unknown, failure.getMessage());
    }

    @Test
    void aPredecessorOfAnotherProjectIsRefusedAsACrossedBoundary() {
        KnowledgeSnapshotMetadata foreign = stores.publish(
                ProjectSpecificationId.generate(), Optional.empty(), KnowledgeSnapshotState.RETIRED);
        stores.publish(projectId, Optional.of(foreign.id()), KnowledgeSnapshotState.ACTIVE);

        PublishedHistoryException failure = assertThrows(PublishedHistoryException.class,
                () -> service.lineage(projectId));

        assertEquals("published lineage crosses project boundary at snapshot " + foreign.id(), failure.getMessage());
    }

    @Test
    void aLineageThatLoopsBackOnItselfIsRefusedAsACycle() {
        KnowledgeSnapshotId headId = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotMetadata retired = stores.publish(
                projectId, Optional.of(headId), KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata head = stores.publishWithId(
                headId, projectId, Optional.of(retired.id()), KnowledgeSnapshotState.ACTIVE);

        PublishedHistoryException failure = assertThrows(PublishedHistoryException.class,
                () -> service.lineage(projectId));

        assertEquals("cycle detected in published snapshot lineage at " + head.id(), failure.getMessage());
    }
}
