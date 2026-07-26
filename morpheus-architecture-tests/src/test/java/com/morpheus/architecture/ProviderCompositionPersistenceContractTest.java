package com.morpheus.architecture;

import com.morpheus.application.composition.ProviderCompositionConflict;
import com.morpheus.application.composition.ProviderCompositionReport;
import com.morpheus.application.composition.ProviderConflictContender;
import com.morpheus.application.composition.ProviderConflictResolution;
import com.morpheus.application.composition.ProviderContribution;
import com.morpheus.application.composition.ProviderContributionStatus;
import com.morpheus.application.composition.ProviderEntityKind;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.memory.MemoryProviderCompositionReportStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteProviderCompositionReportStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderCompositionPersistenceContractTest {

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqlitePersistTheSameCanonicalCompositionReport() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        ProviderCompositionReport report = report();

        var memoryKnowledge = new MemorySpecificationKnowledgeStore();
        seed(memoryKnowledge, projectId, snapshotId);
        var memory = new MemoryProviderCompositionReportStore(memoryKnowledge);
        memory.put(snapshotId, report);

        Path database = tempDir.resolve("composition.db");
        try (var sqliteKnowledge = new SqliteSpecificationKnowledgeStore(database);
             var sqlite = new SqliteProviderCompositionReportStore(database)) {
            seed(sqliteKnowledge, projectId, snapshotId);
            sqlite.put(snapshotId, report);
            assertEquals(memory.find(snapshotId), sqlite.find(snapshotId));
        }
    }

    @Test
    void sqliteReopenPreservesContributionsConflictsAndProvenanceDecision() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        ProviderCompositionReport report = report();
        Path database = tempDir.resolve("reopen.db");

        try (var knowledge = new SqliteSpecificationKnowledgeStore(database);
             var store = new SqliteProviderCompositionReportStore(database)) {
            seed(knowledge, projectId, snapshotId);
            store.put(snapshotId, report);
        }

        try (var reopened = new SqliteProviderCompositionReportStore(database)) {
            assertEquals(report, reopened.find(snapshotId).orElseThrow());
        }
    }

    private void seed(
            com.morpheus.application.store.SpecificationKnowledgeStore store,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId) {
        store.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        store.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("m18-test"),
                Instant.parse("2026-07-26T12:00:00Z")));
    }

    private ProviderCompositionReport report() {
        ProviderConflictContender openSpec = new ProviderConflictContender(
                new ProviderId("openspec"), "01900000-0000-7000-8000-000000000001", 100);
        ProviderConflictContender markdown = new ProviderConflictContender(
                new ProviderId("markdown"), "01900000-0000-7000-8000-000000000002", 50);
        return new ProviderCompositionReport(
                List.of(
                        new ProviderContribution(
                                new ProviderId("openspec"),
                                100,
                                false,
                                ProviderContributionStatus.READ,
                                6,
                                Optional.empty()),
                        new ProviderContribution(
                                new ProviderId("markdown"),
                                50,
                                false,
                                ProviderContributionStatus.READ,
                                3,
                                Optional.of("secondary source"))),
                List.of(new ProviderCompositionConflict(
                        ProviderEntityKind.REQUIREMENT,
                        "payments/reject-invalid",
                        ProviderConflictResolution.RESOLVED_BY_PRECEDENCE,
                        Optional.of(openSpec),
                        List.of(openSpec, markdown),
                        "Higher explicit provider precedence selected the canonical contribution")));
    }
}
