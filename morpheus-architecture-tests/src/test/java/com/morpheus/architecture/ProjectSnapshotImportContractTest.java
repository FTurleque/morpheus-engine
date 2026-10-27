package com.morpheus.architecture;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.ingestion.ProjectContentIngestion;
import com.morpheus.application.ingestion.ProjectSnapshotImportResult;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.security.ServerLocationDisclosure;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.provider.openspec.OpenSpecSpecificationContentReader;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.adapter.SqliteEntityIdentityStore;
import com.morpheus.store.sqlite.adapter.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.adapter.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        var reader = new ProjectContentIngestion(new OpenSpecSpecificationContentReader());
        var identities = new PersistentEntityIdentityResolver(snapshots);
        NormalizedProjectContent content = reader.read(RepositoryRoot.fixture("openspec-basic"), projectId, identities);
        var service = new ProjectSnapshotImportService(snapshots, snapshots, contentStore, traceability);

        ProjectSnapshotImportResult first = service.publishFull(content, Optional.of("rev-1"), T0);
        assertEquals(KnowledgeSnapshotState.ACTIVE, first.snapshot().state());
        assertEquals(2, first.requirementCount());
        assertEquals(16, first.traceabilityLinkCount());
        assertEquals(first.snapshot().id(), snapshots.activeSnapshot(projectId).orElseThrow().id());

        NormalizedProjectContent secondRead = reader.read(RepositoryRoot.fixture("openspec-basic"), projectId, identities);
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
        NormalizedProjectContent valid = new ProjectContentIngestion(new OpenSpecSpecificationContentReader()).read(
                RepositoryRoot.fixture("openspec-basic"), projectId, identities);
        var service = new ProjectSnapshotImportService(snapshots, snapshots, contentStore, traceability);
        ProjectSnapshotImportResult baseline = service.publishFull(valid, Optional.of("rev-1"), T0);

        NormalizedProjectContent invalid = NormalizedProjectContent.withoutAcceptanceCriteria(
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
    void aRejectedPublicationNamesTheFileThatFailedToReadRelativeToTheWorkspace() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        Files.createDirectories(workspace.resolve("openspec/specs/broken"));
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Files.writeString(workspace.resolve("openspec/specs/broken/spec.md"), """
                ## Requirements

                ### Requirement: Untitled
                The system SHALL reject a specification without a title.
                """);
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var service = new ProjectSnapshotImportService(
                snapshots,
                snapshots,
                new MemorySnapshotBusinessContentStore(snapshots, snapshots),
                new MemoryTraceabilityStore(snapshots));
        NormalizedProjectContent content = new OpenSpecSpecificationContentReader()
                .read(ProviderReadRequest.all(workspace, projectId), new PersistentEntityIdentityResolver(snapshots))
                .content()
                .orElseThrow();

        KnowledgeStoreException failure = assertThrows(KnowledgeStoreException.class, () ->
                service.publishFull(content, Optional.of("rev-broken"), T0));

        assertTrue(failure.getMessage().contains("openspec/specs/broken/spec.md"), failure.getMessage());
        assertTrue(failure.getMessage().contains("has no title"), failure.getMessage());
        assertTrue(ServerLocationDisclosure.isSafeToRelay(failure.getMessage()), failure.getMessage());
        assertTrue(snapshots.activeSnapshot(projectId).isEmpty());
    }

    @Test
    void aRejectedPublicationWithholdsABlockingDiagnosticThatNamesAServerLocationAndSaysSo() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var service = new ProjectSnapshotImportService(
                snapshots,
                snapshots,
                new MemorySnapshotBusinessContentStore(snapshots, snapshots),
                new MemoryTraceabilityStore(snapshots));
        NormalizedProjectContent valid = new ProjectContentIngestion(new OpenSpecSpecificationContentReader()).read(
                RepositoryRoot.fixture("openspec-basic"), projectId, new PersistentEntityIdentityResolver(snapshots));
        String absolute = tempDir.resolve("secret/spec.md").toAbsolutePath().toString();
        NormalizedProjectContent invalid = NormalizedProjectContent.withoutAcceptanceCriteria(
                valid.project(), valid.specifications(), valid.requirements(), valid.scenarios(), valid.changes(),
                valid.requirementDeltas(), valid.constraints(), valid.designDecisions(), valid.tasks(), valid.evidence(),
                List.of(
                        Diagnostic.error(DiagnosticCode.INVALID_SOURCE, "cannot read " + absolute, Map.of()),
                        Diagnostic.error(DiagnosticCode.INVALID_SOURCE, "specs/relayable/spec.md: no title", Map.of())));

        KnowledgeStoreException failure = assertThrows(KnowledgeStoreException.class, () ->
                service.publishFull(invalid, Optional.of("rev-bad"), T0));

        assertFalse(failure.getMessage().contains(absolute), failure.getMessage());
        assertTrue(failure.getMessage().contains("specs/relayable/spec.md: no title"), failure.getMessage());
        assertTrue(failure.getMessage().contains("1 withheld"), failure.getMessage());
        assertTrue(ServerLocationDisclosure.isSafeToRelay(failure.getMessage()), failure.getMessage());
    }

    @Test
    void aRejectedPublicationStaysWithinTheRelayedBoundAndCountsTheDiagnosticsItDoesNotShow() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var service = new ProjectSnapshotImportService(
                snapshots,
                snapshots,
                new MemorySnapshotBusinessContentStore(snapshots, snapshots),
                new MemoryTraceabilityStore(snapshots));
        NormalizedProjectContent valid = new ProjectContentIngestion(new OpenSpecSpecificationContentReader()).read(
                RepositoryRoot.fixture("openspec-basic"), projectId, new PersistentEntityIdentityResolver(snapshots));
        List<Diagnostic> blocking = java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(index -> Diagnostic.error(
                        DiagnosticCode.INVALID_SOURCE,
                        "openspec/specs/file-" + index + "/spec.md: OpenSpec specification has no title",
                        Map.of()))
                .toList();
        NormalizedProjectContent invalid = NormalizedProjectContent.withoutAcceptanceCriteria(
                valid.project(), valid.specifications(), valid.requirements(), valid.scenarios(), valid.changes(),
                valid.requirementDeltas(), valid.constraints(), valid.designDecisions(), valid.tasks(), valid.evidence(),
                blocking);

        KnowledgeStoreException failure = assertThrows(KnowledgeStoreException.class, () ->
                service.publishFull(invalid, Optional.of("rev-many"), T0));

        String message = failure.getMessage();
        assertTrue(message.length() <= ServerLocationDisclosure.MAX_RELAYED_LENGTH, message.length() + ": " + message);
        assertTrue(ServerLocationDisclosure.isSafeToRelay(message), message);
        assertTrue(message.contains("openspec/specs/file-1/spec.md"), message);
        assertFalse(message.contains("openspec/specs/file-20/spec.md"), message);
        long shown = java.util.regex.Pattern.compile("INVALID_SOURCE: ").matcher(message).results().count();
        assertTrue(message.endsWith("; " + (20 - shown) + " more not shown"), message);
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
            NormalizedProjectContent normalized = new ProjectContentIngestion(new OpenSpecSpecificationContentReader()).read(
                    RepositoryRoot.fixture("openspec-basic"), projectId, new PersistentEntityIdentityResolver(identities));
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
}