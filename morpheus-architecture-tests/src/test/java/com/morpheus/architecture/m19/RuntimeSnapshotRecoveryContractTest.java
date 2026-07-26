package com.morpheus.architecture.m19;

import com.morpheus.application.snapshot.RuntimeSnapshotRecovery;
import com.morpheus.application.snapshot.SnapshotRecoveryPolicy;
import com.morpheus.application.snapshot.SnapshotRecoveryService;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeSnapshotRecoveryContractTest {

    @Test
    void recoversStaleTechnicalCandidatesAcrossProjectsInDeterministicProjectOrder() {
        MemorySpecificationKnowledgeStore store = new MemorySpecificationKnowledgeStore();
        ProjectSpecificationId projectA = new ProjectSpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1960, 1));
        ProjectSpecificationId projectB = new ProjectSpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1960, 2));
        store.putProject(new ProjectStoreEntry(projectB, SourceLocator.file("m19/recovery-b")));
        store.putProject(new ProjectStoreEntry(projectA, SourceLocator.file("m19/recovery-a")));

        Instant now = Instant.parse("2026-07-26T21:00:00Z");
        KnowledgeSnapshotMetadata staleA = building(projectA, now.minus(Duration.ofMinutes(30)));
        KnowledgeSnapshotMetadata staleB = building(projectB, now.minus(Duration.ofMinutes(20)));
        KnowledgeSnapshotMetadata freshB = building(projectB, now.minus(Duration.ofMinutes(2)));
        store.putSnapshot(staleA);
        store.putSnapshot(staleB);
        store.putSnapshot(freshB);

        SnapshotRecoveryPolicy policy = new SnapshotRecoveryPolicy(Duration.ofMinutes(10));
        RuntimeSnapshotRecovery runtime = new RuntimeSnapshotRecovery(
                store,
                new SnapshotRecoveryService(store),
                policy);

        var reports = runtime.recoverAll(now);

        assertEquals(List.of(projectA, projectB), List.copyOf(reports.keySet()));
        assertEquals(List.of(staleA.id()), reports.get(projectA).recoveredCandidates());
        assertEquals(List.of(staleB.id()), reports.get(projectB).recoveredCandidates());
        assertEquals(KnowledgeSnapshotState.FAILED, store.findSnapshot(staleA.id()).orElseThrow().state());
        assertEquals(KnowledgeSnapshotState.FAILED, store.findSnapshot(staleB.id()).orElseThrow().state());
        assertEquals(KnowledgeSnapshotState.BUILDING, store.findSnapshot(freshB.id()).orElseThrow().state());
    }

    private KnowledgeSnapshotMetadata building(ProjectSpecificationId projectId, Instant createdAt) {
        return new KnowledgeSnapshotMetadata(
                KnowledgeSnapshotId.generate(),
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.BUILDING,
                Optional.empty(),
                createdAt);
    }
}
