package com.morpheus.api;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MorpheusApiRuntimeRecoveryContractTest {

    @TempDir
    Path tempDir;

    @Test
    void serverBootstrapRecoversStaleCandidatesAcrossRegisteredProjects() {
        Path database = tempDir.resolve("runtime-recovery.db");
        ProjectSpecificationId buildingProject = ProjectSpecificationId.generate();
        ProjectSpecificationId validatingProject = ProjectSpecificationId.generate();
        KnowledgeSnapshotId building = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId validating = KnowledgeSnapshotId.generate();
        Instant stale = Instant.now().minus(Duration.ofHours(1));

        try (SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(database)) {
            store.putProject(new ProjectStoreEntry(buildingProject, SourceLocator.file("m19/runtime/building")));
            store.putProject(new ProjectStoreEntry(validatingProject, SourceLocator.file("m19/runtime/validating")));
            store.putSnapshot(new KnowledgeSnapshotMetadata(
                    building, buildingProject, Optional.empty(), KnowledgeSnapshotState.BUILDING,
                    Optional.of("stale-building"), stale));
            store.putSnapshot(new KnowledgeSnapshotMetadata(
                    validating, validatingProject, Optional.empty(), KnowledgeSnapshotState.VALIDATING,
                    Optional.of("stale-validating"), stale));
        }

        try (MorpheusHttpServer ignored = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            // Starting the real composition root is the recovery trigger under test.
        }

        try (SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(database)) {
            assertEquals(KnowledgeSnapshotState.FAILED, store.findSnapshot(building).orElseThrow().state());
            assertEquals(KnowledgeSnapshotState.FAILED, store.findSnapshot(validating).orElseThrow().state());
        }
    }
}
