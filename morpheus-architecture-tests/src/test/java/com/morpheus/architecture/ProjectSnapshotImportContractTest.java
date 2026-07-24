package com.morpheus.architecture;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.ingestion.ProjectSnapshotImportResult;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.provider.openspec.OpenSpecProjectContentReader;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.SqliteEntityIdentityStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectSnapshotImportContractTest {
    private static final Instant T0 = Instant.parse("2026-07-24T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void publishesFullNormalizedGraphAndAtomicallyReplacesActiveSnapshotInMemory() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var contentStore = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        var reader = new OpenSpecProjectContentReader();
        var identities = new PersistentEntityIdentityResolver(snapshots);
        NormalizedProjectContent content = reader.read(fixture("openspec-basic"), projectId, identities);
        var service = new ProjectSnapshotImportService(snapshots, snapshots, contentStore, traceability);

        ProjectSnapshotImportResult first = service.publishFull(content, Optional.of("rev-1"), T0);
        assertEquals(KnowledgeSnapshotState.ACTIVE, first.snapshot().state());
        assertEquals(2, first.requirementCount());
        assertEquals(16, first.traceabilityLinkCount());
        assertEquals(first.snapshot().id(), snapshots.activeSnapshot(projectId).orElseThrow().id());

        NormalizedProjectContent secondRead = reader.read(fixture("openspec-basic"), projectId, identities);
        ProjectSnapshotImportResult second = service.publishFull(secondRead, Optional.of("rev-2"), T0.plusSeconds(60));
        assertNotEquals(first.snapshot().id(), second.snapshot().id());
        assertEquals(KnowledgeSnapshotState.RETIRED, snapshots.findSnapshot(first.snapshot().id()).orElseThrow().state());
        assertEquals(KnowledgeSnapshotState.ACTIVE, snapshots.findSnapshot(second.snapshot().id()).orElseThrow().state());
        assertEquals(Optional.of(first.specificationVersion().id()), second.specificationVersion().predecessor());
        assertEquals(Optional.of(2L), second.specificationVersion().sequence());
    }

    @Test
    void blockingDiagnosticsFailCandidateWithoutReplacingPublishedBaseline() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var contentStore = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        var identities = new PersistentEntityIdentityResolver(snapshots);
        NormalizedProjectContent valid = new OpenSpecProjectContentReader().read(
                fixture("openspec-basic"), projectId, identities);
        var service = new ProjectSnapshotImportService(snapshots, snapshots, contentStore, traceability);
        ProjectSnapshotImportResult baseline = service.publishFull(valid, Optional.of("rev-1"), T0);

        NormalizedProjectContent invalid = new NormalizedProjectContent(
                valid.project(), valid.specifications(), valid.requirements(), valid.scenarios(), valid.changes(),
                valid.requirementDeltas(), valid.constraints(), valid.designDecisions(), valid.tasks(), valid.evidence(),
                List.of(Diagnostic.error(
                        DiagnosticCode.PARTIAL_INGESTION,
                        "blocking test diagnostic",
                        Map.of("test", "m9"))));

        assertThrows(KnowledgeStoreException.class, () ->
                service.publishFull(invalid, Optional.of("rev-bad"), T0.plusSeconds(30)));
        assertEquals(baseline.snapshot().id(), snapshots.activeSnapshot(projectId).orElseThrow().id());
    }

    @Test
    void sqliteReopenPreservesPublishedImport() {
        Path database = tempDir.resolve("m9-import.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        String snapshotId;

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var identities = new SqliteEntityIdentityStore(database)) {
            NormalizedProjectContent normalized = new OpenSpecProjectContentReader().read(
                    fixture("openspec-basic"), projectId, new PersistentEntityIdentityResolver(identities));
            ProjectSnapshotImportResult imported = new ProjectSnapshotImportService(
                    snapshots, requirements, content, traceability)
                    .publishFull(normalized, Optional.of("sqlite-rev"), T0);
            snapshotId = imported.snapshot().id().toString();
            assertEquals(2, requirements.listRequirementVersions(imported.snapshot().id()).size());
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            var active = snapshots.activeSnapshot(projectId).orElseThrow();
            assertEquals(snapshotId, active.id().toString());
            assertEquals(KnowledgeSnapshotState.ACTIVE, active.state());
            assertEquals(2, requirements.listRequirementVersions(active.id()).size());
            assertEquals(1, content.findSnapshotContent(active.id()).orElseThrow().changes().size());
        }
    }

    private Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = current.resolve("experiments/m0/fixtures").resolve(name);
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }
        Path fromModule = current.resolve("../experiments/m0/fixtures").normalize().resolve(name);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("M0 fixture not found: " + name + " from " + current);
    }
}