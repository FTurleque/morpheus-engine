package com.morpheus.architecture;

import com.morpheus.application.reference.ExternalReferenceResolver;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.reference.ExternalReferenceResolverResult;
import com.morpheus.application.reference.LiveExternalReferenceResolutionService;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.reference.ResolvedExternalTarget;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.memory.MemoryExternalReferenceStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveExternalReferenceResolutionContractTest {
    private static final Instant T0 = Instant.parse("2026-07-24T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T0.plusSeconds(30), ZoneOffset.UTC);

    @TempDir
    Path tempDirectory;

    @Test
    void memoryLiveResolutionReturnsObservedCopyAndLeavesActiveReferenceUntouched() {
        MemorySpecificationKnowledgeStore snapshots = new MemorySpecificationKnowledgeStore();
        MemoryExternalReferenceStore references = new MemoryExternalReferenceStore(snapshots);
        verifyLiveResolution(snapshots, references);
    }

    @Test
    void sqliteLiveResolutionAndReopenPreserveOriginalPublishedReference() {
        Path database = tempDirectory.resolve("live-external-resolution.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        DomainIdentity ownerId = DomainIdentity.generate();
        ExternalReference reference = reference(ownerId);

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            activate(snapshots, projectId, snapshotId);
            references.putReference(snapshotId, reference);
            var result = service(snapshots, references).resolveActive(projectId, reference.id()).orElseThrow();
            assertEquals(ExternalReferenceResolutionState.RESOLVED, result.observedReference().resolutionState());
            assertEquals(reference, references.findReference(snapshotId, reference.id()).orElseThrow());
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            assertEquals(reference, references.findReference(snapshotId, reference.id()).orElseThrow());
            assertEquals(ExternalReferenceResolutionState.UNVALIDATED,
                    references.findReference(snapshotId, reference.id()).orElseThrow().resolutionState());
        }
    }

    private void verifyLiveResolution(SpecificationKnowledgeStore snapshots, ExternalReferenceStore references) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        DomainIdentity ownerId = DomainIdentity.generate();
        activate(snapshots, projectId, snapshotId);
        ExternalReference reference = reference(ownerId);
        references.putReference(snapshotId, reference);

        var service = service(snapshots, references);
        var result = service.resolveActive(projectId, reference.id()).orElseThrow();

        assertEquals(reference, result.storedReference());
        assertEquals(ExternalReferenceResolutionState.RESOLVED, result.observedReference().resolutionState());
        assertEquals(ExternalReferenceResolutionReason.RESOLVED, result.observedReference().resolutionReason());
        assertEquals(1, result.observedReference().history().size());
        assertEquals(reference, references.findReference(snapshotId, reference.id()).orElseThrow());
        assertEquals(List.of(reference), service.listActive(projectId, ownerId).orElseThrow());
    }

    private LiveExternalReferenceResolutionService service(
            SpecificationKnowledgeStore snapshots,
            ExternalReferenceStore references) {
        ExternalReferenceResolver resolver = new ExternalReferenceResolver() {
            @Override
            public String system() {
                return "MINOS";
            }

            @Override
            public ExternalReferenceResolverResult resolve(ExternalReferenceTarget target) {
                return ExternalReferenceResolverResult.found(
                        new ResolvedExternalTarget(target, Map.of("minos.symbolKey", target.externalId())));
            }
        };
        return new LiveExternalReferenceResolutionService(
                snapshots, references, new ExternalReferenceResolverRegistry(List.of(resolver)), CLOCK);
    }

    private ExternalReference reference(DomainIdentity ownerId) {
        return ExternalReference.unvalidated(
                ExternalReferenceId.generate(),
                ownerId,
                new ExternalReferenceTarget(
                        "MINOS",
                        Optional.of("morpheus-engine"),
                        "SYMBOL",
                        "symbol:RequirementService",
                        Optional.of("minos-snapshot-42")),
                Optional.empty());
    }

    private void activate(
            SpecificationKnowledgeStore snapshots,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId) {
        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("source-revision"),
                T0));
        snapshots.activateSnapshot(snapshotId, Optional.empty());
    }
}
