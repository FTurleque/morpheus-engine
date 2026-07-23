package com.morpheus.architecture;

import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessContentQueryBackendParityTest {
    private static final Instant T0 = Instant.parse("2026-07-23T16:15:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteProduceExactlySameBoundedChangePage() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        Evidence evidence = new Evidence(
                EvidenceId.generate(), SourceLocator.file("specs/changes.md"), Optional.empty(), Optional.of("sha256:changes"));
        Provenance provenance = new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1"),
                evidence.source(),
                Optional.of("changes"),
                Optional.of("revision-1"),
                evidence.id());
        ChangeProposal first = change(projectId, provenance, "CHG-1", "First");
        ChangeProposal second = change(projectId, provenance, "CHG-2", "Second");
        ChangeProposal third = change(projectId, provenance, "CHG-3", "Third");
        SnapshotBusinessContent projection = new SnapshotBusinessContent(
                snapshotId,
                versionId,
                List.of(),
                List.of(),
                List.of(third, first, second),
                List.of(),
                List.of(),
                List.of(),
                List.of(evidence));

        MemorySpecificationKnowledgeStore memoryCore = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        seed(memoryCore, memoryCore, memoryContent, projectId, snapshotId, versionId, projection);
        SnapshotPage<ChangeProposal> memoryPage = new BusinessContentQueryService(memoryCore, memoryContent)
                .listActiveChanges(projectId, new PageRequest(1, 1))
                .orElseThrow();

        Path database = tempDir.resolve("backend-parity.db");
        SnapshotPage<ChangeProposal> sqlitePage;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            seed(snapshots, versions, content, projectId, snapshotId, versionId, projection);
            sqlitePage = new BusinessContentQueryService(snapshots, content)
                    .listActiveChanges(projectId, new PageRequest(1, 1))
                    .orElseThrow();
        }

        assertEquals(memoryPage, sqlitePage);
        assertEquals(projection.changes().subList(1, 2), memoryPage.items());
        assertEquals(3, memoryPage.totalMatches());
        assertEquals(true, memoryPage.hasMore());
    }

    private void seed(
            com.morpheus.application.store.SpecificationKnowledgeStore snapshots,
            com.morpheus.application.store.VersionedRequirementStore versions,
            com.morpheus.application.store.SnapshotBusinessContentStore content,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            SnapshotBusinessContent projection) {
        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY, Optional.of("revision-1"), T0));
        versions.putSpecificationVersion(new SpecificationVersion(
                versionId,
                projectId,
                Optional.of(1L),
                Optional.of("provider-v1"),
                Optional.of("revision-1"),
                T0,
                Optional.empty()));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        content.putSnapshotContent(projection);
        snapshots.activateSnapshot(snapshotId, Optional.empty());
    }

    private ChangeProposal change(
            ProjectSpecificationId projectId,
            Provenance provenance,
            String key,
            String title) {
        return new ChangeProposal(
                ChangeId.generate(),
                projectId,
                Optional.of(key),
                title,
                "Deliver " + title,
                List.of("scope"),
                List.of(),
                List.of(),
                provenance);
    }
}
