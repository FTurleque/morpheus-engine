package com.morpheus.architecture;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotConflictException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecificationKnowledgeStoreContractTest {
    private static final Instant T0 = Instant.parse("2026-07-22T12:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryStoreSatisfiesProjectAndSnapshotContract() {
        verifyProjectAndSnapshotContract(new MemorySpecificationKnowledgeStore());
    }

    @Test
    void sqliteStoreSatisfiesProjectAndSnapshotContract() {
        try (var store = new SqliteSpecificationKnowledgeStore(tempDir.resolve("contract.db"))) {
            verifyProjectAndSnapshotContract(store);
        }
    }

    @Test
    void memoryStoreRejectsIdentityAndRootCollisions() {
        verifyIdentityAndRootCollisions(new MemorySpecificationKnowledgeStore());
    }

    @Test
    void sqliteStoreRejectsIdentityAndRootCollisions() {
        try (var store = new SqliteSpecificationKnowledgeStore(tempDir.resolve("collisions.db"))) {
            verifyIdentityAndRootCollisions(store);
        }
    }

    private void verifyProjectAndSnapshotContract(SpecificationKnowledgeStore store) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceLocator root = new SourceLocator("file", "workspace");
        ProjectStoreEntry project = new ProjectStoreEntry(projectId, root);
        store.putProject(project);
        store.putProject(project);
        assertEquals(project, store.findProject(projectId).orElseThrow());
        assertEquals(project, store.findProjectByRoot(root).orElseThrow());
        assertEquals(1, store.listProjects().size());
        assertEquals(
                store.listProjects().stream().sorted(Comparator.comparing(ProjectStoreEntry::id)).toList(),
                store.listProjects());

        KnowledgeSnapshotMetadata lifecycleCandidate = buildingSnapshot(
                projectId,
                Optional.empty(),
                "lifecycle-candidate",
                T0.minusSeconds(1));
        store.putSnapshot(lifecycleCandidate);
        assertEquals(
                KnowledgeSnapshotState.VALIDATING,
                store.transitionSnapshotState(
                        lifecycleCandidate.id(),
                        KnowledgeSnapshotState.BUILDING,
                        KnowledgeSnapshotState.VALIDATING).state());
        assertThrows(
                SnapshotConflictException.class,
                () -> store.transitionSnapshotState(
                        lifecycleCandidate.id(),
                        KnowledgeSnapshotState.BUILDING,
                        KnowledgeSnapshotState.READY));
        assertEquals(
                KnowledgeSnapshotState.FAILED,
                store.transitionSnapshotState(
                        lifecycleCandidate.id(),
                        KnowledgeSnapshotState.VALIDATING,
                        KnowledgeSnapshotState.FAILED).state());
        assertThrows(
                SnapshotConflictException.class,
                () -> store.transitionSnapshotState(
                        lifecycleCandidate.id(),
                        KnowledgeSnapshotState.FAILED,
                        KnowledgeSnapshotState.ACTIVE));
        assertTrue(store.activeSnapshot(projectId).isEmpty());

        KnowledgeSnapshotMetadata first = readySnapshot(projectId, Optional.empty(), "revision-1", T0);
        store.putSnapshot(first);
        store.putSnapshot(first);
        assertTrue(store.activeSnapshot(projectId).isEmpty());

        KnowledgeSnapshotMetadata firstActive = store.activateSnapshot(first.id(), Optional.empty());
        assertEquals(KnowledgeSnapshotState.ACTIVE, firstActive.state());
        assertEquals(first.id(), store.activeSnapshot(projectId).orElseThrow().id());
        store.putSnapshot(first);
        assertEquals(KnowledgeSnapshotState.ACTIVE, store.findSnapshot(first.id()).orElseThrow().state());

        KnowledgeSnapshotMetadata second = readySnapshot(
                projectId,
                Optional.of(first.id()),
                "revision-2",
                T0.plusSeconds(1));
        store.putSnapshot(second);

        KnowledgeSnapshotMetadata secondActive = store.activateSnapshot(second.id(), Optional.of(first.id()));
        assertEquals(KnowledgeSnapshotState.ACTIVE, secondActive.state());
        assertEquals(second.id(), store.activeSnapshot(projectId).orElseThrow().id());
        assertEquals(
                KnowledgeSnapshotState.RETIRED,
                store.findSnapshot(first.id()).orElseThrow().state());
        store.putSnapshot(second);
        assertEquals(KnowledgeSnapshotState.ACTIVE, store.findSnapshot(second.id()).orElseThrow().state());

        KnowledgeSnapshotMetadata replay = store.activateSnapshot(second.id(), Optional.of(first.id()));
        assertEquals(secondActive, replay);

        KnowledgeSnapshotMetadata stale = readySnapshot(
                projectId,
                Optional.of(first.id()),
                "stale-revision",
                T0.plusSeconds(2));
        store.putSnapshot(stale);

        assertThrows(
                SnapshotConflictException.class,
                () -> store.activateSnapshot(stale.id(), Optional.of(first.id())));
        assertEquals(second.id(), store.activeSnapshot(projectId).orElseThrow().id());
    }

    private void verifyIdentityAndRootCollisions(SpecificationKnowledgeStore store) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceLocator root = new SourceLocator("file", "workspace-a");
        ProjectStoreEntry originalProject = new ProjectStoreEntry(projectId, root);
        store.putProject(originalProject);

        assertThrows(
                KnowledgeStoreException.class,
                () -> store.putProject(new ProjectStoreEntry(
                        projectId,
                        new SourceLocator("file", "workspace-b"))));

        assertThrows(
                KnowledgeStoreException.class,
                () -> store.putProject(new ProjectStoreEntry(
                        ProjectSpecificationId.generate(),
                        root)));

        KnowledgeSnapshotMetadata originalSnapshot = readySnapshot(
                projectId,
                Optional.empty(),
                "revision-a",
                T0);
        store.putSnapshot(originalSnapshot);

        KnowledgeSnapshotMetadata collidingSnapshot = new KnowledgeSnapshotMetadata(
                originalSnapshot.id(),
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("revision-b"),
                T0);

        assertThrows(KnowledgeStoreException.class, () -> store.putSnapshot(collidingSnapshot));
    }

    private KnowledgeSnapshotMetadata buildingSnapshot(
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessorId,
            String sourceRevision,
            Instant createdAt) {
        return new KnowledgeSnapshotMetadata(
                KnowledgeSnapshotId.generate(),
                projectId,
                predecessorId,
                KnowledgeSnapshotState.BUILDING,
                Optional.of(sourceRevision),
                createdAt);
    }

    private KnowledgeSnapshotMetadata readySnapshot(
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessorId,
            String sourceRevision,
            Instant createdAt) {
        return new KnowledgeSnapshotMetadata(
                KnowledgeSnapshotId.generate(),
                projectId,
                predecessorId,
                KnowledgeSnapshotState.READY,
                Optional.of(sourceRevision),
                createdAt);
    }
}
