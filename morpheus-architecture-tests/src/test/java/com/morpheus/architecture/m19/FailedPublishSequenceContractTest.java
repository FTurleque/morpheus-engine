package com.morpheus.architecture.m19;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.provider.openspec.OpenSpecProjectContentReader;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FailedPublishSequenceContractTest {
    private static final Instant T0 = Instant.parse("2026-08-19T18:00:00Z");

    @Test
    void failedCandidateConsumesSequenceAndRetryKeepsPublishedPredecessor() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var business = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        var identities = new PersistentEntityIdentityResolver(snapshots);
        var reader = new OpenSpecProjectContentReader();
        NormalizedProjectContent content = reader.read(fixture("openspec-basic"), projectId, identities);

        var publisher = new ProjectSnapshotImportService(snapshots, snapshots, business, traceability);
        var first = publisher.publishFull(content, Optional.of("rev-1"), T0);
        assertEquals(Optional.of(1L), first.specificationVersion().sequence());

        var failingPublisher = new ProjectSnapshotImportService(
                snapshots, snapshots, business, new FailingTraceabilityStore(traceability));
        assertThrows(KnowledgeStoreException.class, () ->
                failingPublisher.publishFull(content, Optional.of("rev-failed"), T0.plusSeconds(1)));

        var failedSnapshot = snapshots.listSnapshots(projectId).stream()
                .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.FAILED)
                .findFirst()
                .orElseThrow();
        var failedBinding = snapshots.findSnapshotVersion(failedSnapshot.id()).orElseThrow();
        var failedVersion = snapshots.findSpecificationVersion(failedBinding.specificationVersionId()).orElseThrow();
        assertEquals(Optional.of(2L), failedVersion.sequence());

        var retry = publisher.publishFull(content, Optional.of("rev-2"), T0.plusSeconds(2));
        assertEquals(Optional.of(3L), retry.specificationVersion().sequence());
        assertEquals(Optional.of(first.specificationVersion().id()), retry.specificationVersion().predecessor());
        assertEquals(retry.snapshot().id(), snapshots.activeSnapshot(projectId).orElseThrow().id());
    }

    private Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = current.resolve("experiments/m0/fixtures").resolve(name);
        if (Files.isDirectory(fromRoot)) return fromRoot;
        Path fromModule = current.resolve("../experiments/m0/fixtures").normalize().resolve(name);
        if (Files.isDirectory(fromModule)) return fromModule;
        throw new IllegalStateException("M0 fixture not found: " + name + " from " + current);
    }

    private record FailingTraceabilityStore(TraceabilityStore delegate) implements TraceabilityStore {
        @Override
        public void putLink(com.morpheus.domain.snapshot.KnowledgeSnapshotId snapshotId, TraceabilityLink link) {
            delegate.putLink(snapshotId, link);
        }

        @Override
        public void putLinks(com.morpheus.domain.snapshot.KnowledgeSnapshotId snapshotId, List<TraceabilityLink> links) {
            throw new KnowledgeStoreException("injected traceability failure");
        }

        @Override
        public Optional<TraceabilityLink> findLink(
                com.morpheus.domain.snapshot.KnowledgeSnapshotId snapshotId,
                TraceabilityLinkId linkId) {
            return delegate.findLink(snapshotId, linkId);
        }

        @Override
        public List<TraceabilityLink> outgoing(
                com.morpheus.domain.snapshot.KnowledgeSnapshotId snapshotId,
                TraceabilityEntityRef source,
                Set<TraceabilityRelationType> relationTypes) {
            return delegate.outgoing(snapshotId, source, relationTypes);
        }

        @Override
        public List<TraceabilityLink> incoming(
                com.morpheus.domain.snapshot.KnowledgeSnapshotId snapshotId,
                TraceabilityEntityRef target,
                Set<TraceabilityRelationType> relationTypes) {
            return delegate.incoming(snapshotId, target, relationTypes);
        }
    }
}
