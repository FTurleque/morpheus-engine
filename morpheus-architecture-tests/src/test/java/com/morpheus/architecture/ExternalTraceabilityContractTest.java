package com.morpheus.architecture;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.traceability.ExternalTraceabilityAvailability;
import com.morpheus.application.traceability.ExternalTraceabilityLinkFactory;
import com.morpheus.application.traceability.ExternalTraceabilityQueryService;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
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
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.store.memory.MemoryExternalReferenceStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalTraceabilityContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T12:30:00Z");
    private final ExternalTraceabilityLinkFactory linkFactory = new ExternalTraceabilityLinkFactory();

    @TempDir
    Path tempDir;

    @Test
    void memoryRoundTripsExternalReferencesAndResolvedTraceabilityView() {
        var snapshots = new MemorySpecificationKnowledgeStore();
        verifyRoundTrip(
                snapshots,
                new MemoryExternalReferenceStore(snapshots),
                new MemoryTraceabilityStore(snapshots));
    }

    @Test
    void sqliteRoundTripsExternalReferencesAndResolvedTraceabilityView() {
        Path database = tempDir.resolve("external-roundtrip.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var references = new SqliteExternalReferenceStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            verifyRoundTrip(snapshots, references, traceability);
        }
    }

    @Test
    void sqliteReopenPreservesCoordinatesResolvedAttributesProvenanceHistoryAndLink() {
        Path database = tempDir.resolve("external-reopen.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        DomainIdentity owner = DomainIdentity.generate();
        ExternalReference reference = resolvedReference(owner);
        TraceabilityEntityRef source = new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, owner);
        var link = linkFactory.create(
                TraceabilityLinkId.generate(),
                source,
                TraceabilityRelationType.LINKS_TO_CODE,
                reference,
                TraceabilityLinkOrigin.EXPLICIT,
                Optional.empty(),
                Set.of(EvidenceId.generate()),
                T0.plusSeconds(10));

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var references = new SqliteExternalReferenceStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            createProjectAndSnapshots(snapshots, projectId, snapshotId, KnowledgeSnapshotId.generate());
            references.putReference(snapshotId, reference);
            traceability.putLink(snapshotId, link);
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var references = new SqliteExternalReferenceStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            assertTrue(snapshots.findSnapshot(snapshotId).isPresent());
            assertEquals(reference, references.findReference(snapshotId, reference.id()).orElseThrow());
            assertEquals(link, traceability.findLink(snapshotId, link.id()).orElseThrow());
            var view = new ExternalTraceabilityQueryService(traceability, references)
                    .outgoing(snapshotId, source, Set.of(TraceabilityRelationType.LINKS_TO_CODE))
                    .getFirst();
            assertEquals(ExternalTraceabilityAvailability.REFERENCE_RESOLVED, view.availability());
            assertEquals(reference, view.reference().orElseThrow());
        }
    }

    @Test
    void brokenReferenceRemainsVisibleOnMemoryAndSQLite() {
        var memorySnapshots = new MemorySpecificationKnowledgeStore();
        verifyBrokenReference(
                memorySnapshots,
                new MemoryExternalReferenceStore(memorySnapshots),
                new MemoryTraceabilityStore(memorySnapshots));

        Path database = tempDir.resolve("external-broken.db");
        try (var sqliteSnapshots = new SqliteSpecificationKnowledgeStore(database);
             var sqliteReferences = new SqliteExternalReferenceStore(database);
             var sqliteTraceability = new SqliteTraceabilityStore(database)) {
            verifyBrokenReference(sqliteSnapshots, sqliteReferences, sqliteTraceability);
        }
    }

    @Test
    void sameIdentityMayDifferAcrossSnapshotsButCannotMutateInsideOneSnapshot() {
        var memorySnapshots = new MemorySpecificationKnowledgeStore();
        verifySnapshotImmutability(memorySnapshots, new MemoryExternalReferenceStore(memorySnapshots));

        Path database = tempDir.resolve("external-snapshot-rules.db");
        try (var sqliteSnapshots = new SqliteSpecificationKnowledgeStore(database);
             var sqliteReferences = new SqliteExternalReferenceStore(database)) {
            verifySnapshotImmutability(sqliteSnapshots, sqliteReferences);
        }
    }

    private void verifyRoundTrip(
            SpecificationKnowledgeStore snapshots,
            ExternalReferenceStore references,
            TraceabilityStore traceability) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        createProjectAndSnapshots(snapshots, projectId, snapshotId, KnowledgeSnapshotId.generate());

        DomainIdentity owner = DomainIdentity.generate();
        ExternalReference reference = resolvedReference(owner);
        references.putReference(snapshotId, reference);
        references.putReference(snapshotId, reference);

        assertEquals(reference, references.findReference(snapshotId, reference.id()).orElseThrow());
        assertEquals(List.of(reference), references.findByOwner(snapshotId, owner));

        TraceabilityEntityRef source = new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, owner);
        var link = linkFactory.create(
                TraceabilityLinkId.generate(),
                source,
                TraceabilityRelationType.LINKS_TO_CODE,
                reference,
                TraceabilityLinkOrigin.EXPLICIT,
                Optional.empty(),
                Set.of(EvidenceId.generate()),
                T0.plusSeconds(10));
        traceability.putLink(snapshotId, link);

        var views = new ExternalTraceabilityQueryService(traceability, references)
                .outgoing(snapshotId, source, Set.of(TraceabilityRelationType.LINKS_TO_CODE));
        assertEquals(1, views.size());
        assertEquals(link, views.getFirst().link());
        assertEquals(reference, views.getFirst().reference().orElseThrow());
        assertEquals(ExternalTraceabilityAvailability.REFERENCE_RESOLVED, views.getFirst().availability());
    }

    private void verifyBrokenReference(
            SpecificationKnowledgeStore snapshots,
            ExternalReferenceStore references,
            TraceabilityStore traceability) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        createProjectAndSnapshots(snapshots, projectId, snapshotId, KnowledgeSnapshotId.generate());

        DomainIdentity owner = DomainIdentity.generate();
        ExternalReference reference = ExternalReference.unvalidated(
                ExternalReferenceId.generate(),
                owner,
                target(),
                Optional.empty());
        TraceabilityEntityRef source = new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, owner);
        var link = linkFactory.create(
                TraceabilityLinkId.generate(),
                source,
                TraceabilityRelationType.LINKS_TO_TEST,
                reference,
                TraceabilityLinkOrigin.EXPLICIT,
                Optional.empty(),
                Set.of(EvidenceId.generate()),
                T0);

        traceability.putLink(snapshotId, link);
        var views = new ExternalTraceabilityQueryService(traceability, references)
                .outgoing(snapshotId, source, Set.of());

        assertEquals(1, views.size());
        assertEquals(link, views.getFirst().link());
        assertEquals(Optional.empty(), views.getFirst().reference());
        assertEquals(ExternalTraceabilityAvailability.BROKEN_REFERENCE, views.getFirst().availability());
    }

    private void verifySnapshotImmutability(
            SpecificationKnowledgeStore snapshots,
            ExternalReferenceStore references) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId first = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId second = KnowledgeSnapshotId.generate();
        createProjectAndSnapshots(snapshots, projectId, first, second);

        DomainIdentity owner = DomainIdentity.generate();
        ExternalReference original = ExternalReference.unvalidated(
                ExternalReferenceId.generate(), owner, target(), Optional.empty());
        ExternalReference changed = original.transition(
                ExternalReferenceResolutionState.UNRESOLVED,
                ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                Optional.empty(),
                T0.plusSeconds(1));

        references.putReference(first, original);
        assertThrows(KnowledgeStoreException.class, () -> references.putReference(first, changed));
        references.putReference(second, changed);

        assertEquals(original, references.findReference(first, original.id()).orElseThrow());
        assertEquals(changed, references.findReference(second, original.id()).orElseThrow());
    }

    private ExternalReference resolvedReference(DomainIdentity owner) {
        Provenance provenance = new Provenance(
                new ProviderId("openspec"),
                Optional.of("1.0"),
                SourceLocator.file("specs/payments.md"),
                Optional.of("ext-ref-42"),
                Optional.of("source-rev-7"),
                EvidenceId.generate());
        ExternalReference unvalidated = ExternalReference.unvalidated(
                ExternalReferenceId.generate(),
                owner,
                target(),
                Optional.of(provenance));
        ExternalReference unresolved = unvalidated.transition(
                ExternalReferenceResolutionState.UNRESOLVED,
                ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                Optional.empty(),
                T0.plusSeconds(1));
        return unresolved.transition(
                ExternalReferenceResolutionState.RESOLVED,
                ExternalReferenceResolutionReason.RESOLVED,
                Optional.of(new ResolvedExternalTarget(
                        target(),
                        Map.of("symbolKind", "class", "language", "java"))),
                T0.plusSeconds(2));
    }

    private ExternalReferenceTarget target() {
        return new ExternalReferenceTarget(
                "MINOS",
                Optional.of("morpheus-engine"),
                "CODE_SYMBOL",
                "com.morpheus.Payments",
                Optional.of("rev-42"));
    }

    private void createProjectAndSnapshots(
            SpecificationKnowledgeStore snapshots,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId first,
            KnowledgeSnapshotId second) {
        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
        snapshots.putSnapshot(snapshot(first, projectId, "revision-1"));
        snapshots.putSnapshot(snapshot(second, projectId, "revision-2"));
    }

    private KnowledgeSnapshotMetadata snapshot(
            KnowledgeSnapshotId id,
            ProjectSpecificationId projectId,
            String revision) {
        return new KnowledgeSnapshotMetadata(
                id,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of(revision),
                T0);
    }
}
