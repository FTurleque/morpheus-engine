package com.morpheus.store.sqlite;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteSnapshotLifecyclePersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void activeSnapshotAndRetiredPredecessorSurviveReopen() {
        Path database = tempDir.resolve("snapshot-lifecycle.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId firstId = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId secondId = KnowledgeSnapshotId.generate();

        try (var store = new SqliteSpecificationKnowledgeStore(database)) {
            store.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));

            KnowledgeSnapshotMetadata first = new KnowledgeSnapshotMetadata(
                    firstId,
                    projectId,
                    Optional.empty(),
                    KnowledgeSnapshotState.BUILDING,
                    Optional.of("revision-1"),
                    Instant.parse("2026-07-22T12:00:00Z"));
            store.putSnapshot(first);
            store.transitionSnapshotState(firstId, KnowledgeSnapshotState.BUILDING, KnowledgeSnapshotState.VALIDATING);
            store.transitionSnapshotState(firstId, KnowledgeSnapshotState.VALIDATING, KnowledgeSnapshotState.READY);
            store.activateSnapshot(firstId, Optional.empty());

            KnowledgeSnapshotMetadata second = new KnowledgeSnapshotMetadata(
                    secondId,
                    projectId,
                    Optional.of(firstId),
                    KnowledgeSnapshotState.BUILDING,
                    Optional.of("revision-2"),
                    Instant.parse("2026-07-22T12:00:01Z"));
            store.putSnapshot(second);
            store.transitionSnapshotState(secondId, KnowledgeSnapshotState.BUILDING, KnowledgeSnapshotState.VALIDATING);
            store.transitionSnapshotState(secondId, KnowledgeSnapshotState.VALIDATING, KnowledgeSnapshotState.READY);
            store.activateSnapshot(secondId, Optional.of(firstId));
        }

        try (var reopened = new SqliteSpecificationKnowledgeStore(database)) {
            assertEquals(secondId, reopened.activeSnapshot(projectId).orElseThrow().id());
            assertEquals(KnowledgeSnapshotState.ACTIVE, reopened.findSnapshot(secondId).orElseThrow().state());
            assertEquals(KnowledgeSnapshotState.RETIRED, reopened.findSnapshot(firstId).orElseThrow().state());
        }
    }
}
