package com.morpheus.architecture.m19;

import com.morpheus.application.snapshot.SnapshotLifecycleService;
import com.morpheus.application.snapshot.SnapshotRecoveryService;
import com.morpheus.application.store.ProjectStoreEntry;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotRecoveryContractTest {

    @TempDir
    Path tempDir;

    @Test
    void memoryRecoveryFailsOnlyStaleTechnicalCandidatesAndPreservesActive() {
        assertRecoveryContract(new MemorySpecificationKnowledgeStore());
    }

    @Test
    void sqliteRecoveryFailsOnlyStaleTechnicalCandidatesAndPreservesActiveAcrossReopen() {
        Path database = tempDir.resolve("recovery.db");
        ProjectSpecificationId projectId;
        KnowledgeSnapshotId activeId;
        KnowledgeSnapshotId staleId;
        KnowledgeSnapshotId freshId;

        try (SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(database)) {
            RecoveryIds ids = exerciseRecovery(store);
            projectId = ids.projectId();
            activeId = ids.activeId();
            staleId = ids.staleId();
            freshId = ids.freshId();
        }

        try (SqliteSpecificationKnowledgeStore reopened = new SqliteSpecificationKnowledgeStore(database)) {
            assertEquals(activeId, reopened.activeSnapshot(projectId).orElseThrow().id());
            assertEquals(KnowledgeSnapshotState.FAILED, reopened.findSnapshot(staleId).orElseThrow().state());
            assertEquals(KnowledgeSnapshotState.BUILDING, reopened.findSnapshot(freshId).orElseThrow().state());
        }
    }

    private void assertRecoveryContract(SpecificationKnowledgeStore store) {
        RecoveryIds ids = exerciseRecovery(store);
        assertEquals(ids.activeId(), store.activeSnapshot(ids.projectId()).orElseThrow().id());
        assertEquals(KnowledgeSnapshotState.FAILED, store.findSnapshot(ids.staleId()).orElseThrow().state());
        assertEquals(KnowledgeSnapshotState.BUILDING, store.findSnapshot(ids.freshId()).orElseThrow().state());
    }

    private RecoveryIds exerciseRecovery(SpecificationKnowledgeStore store) {
        Instant t0 = Instant.parse("2026-07-26T10:00:00Z");
        Instant cutoff = t0.plusSeconds(600);
        ProjectSpecificationId projectId = new ProjectSpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1900, 1));
        store.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("/m19/recovery")));
        SnapshotLifecycleService lifecycle = new SnapshotLifecycleService(store);

        KnowledgeSnapshotMetadata activeCandidate = building(projectId, Optional.empty(), t0, "active");
        lifecycle.registerBuilding(activeCandidate);
        store.transitionSnapshotState(activeCandidate.id(), KnowledgeSnapshotState.BUILDING, KnowledgeSnapshotState.VALIDATING);
        store.transitionSnapshotState(activeCandidate.id(), KnowledgeSnapshotState.VALIDATING, KnowledgeSnapshotState.READY);
        KnowledgeSnapshotMetadata active = lifecycle.activate(activeCandidate.id());

        KnowledgeSnapshotMetadata stale = building(projectId, Optional.of(active.id()), t0.plusSeconds(60), "stale");
        lifecycle.registerBuilding(stale);
        store.transitionSnapshotState(stale.id(), KnowledgeSnapshotState.BUILDING, KnowledgeSnapshotState.VALIDATING);

        KnowledgeSnapshotMetadata fresh = building(projectId, Optional.of(active.id()), cutoff.plusSeconds(1), "fresh");
        lifecycle.registerBuilding(fresh);

        SnapshotRecoveryService.RecoveryReport report = new SnapshotRecoveryService(store)
                .recoverStaleCandidates(projectId, cutoff);

        assertEquals(java.util.List.of(stale.id()), report.recoveredCandidates());
        assertEquals(java.util.List.of(), report.racedCandidates());
        assertEquals(active.id(), store.activeSnapshot(projectId).orElseThrow().id());
        assertEquals(KnowledgeSnapshotState.ACTIVE, store.findSnapshot(active.id()).orElseThrow().state());
        assertEquals(KnowledgeSnapshotState.FAILED, store.findSnapshot(stale.id()).orElseThrow().state());
        assertEquals(KnowledgeSnapshotState.BUILDING, store.findSnapshot(fresh.id()).orElseThrow().state());

        return new RecoveryIds(projectId, active.id(), stale.id(), fresh.id());
    }

    private KnowledgeSnapshotMetadata building(
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessor,
            Instant createdAt,
            String revision) {
        return new KnowledgeSnapshotMetadata(
                KnowledgeSnapshotId.generate(),
                projectId,
                predecessor,
                KnowledgeSnapshotState.BUILDING,
                Optional.of(revision),
                createdAt);
    }

    private record RecoveryIds(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId activeId,
            KnowledgeSnapshotId staleId,
            KnowledgeSnapshotId freshId) {
    }
}
