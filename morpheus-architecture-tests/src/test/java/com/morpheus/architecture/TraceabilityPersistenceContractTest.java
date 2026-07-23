package com.morpheus.architecture;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceabilityPersistenceContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryStoreRoundTripsSnapshotScopedLinksAndDirectQueries() {
        var snapshots = new MemorySpecificationKnowledgeStore();
        verifyRoundTripAndQueries(snapshots, new MemoryTraceabilityStore(snapshots));
    }

    @Test
    void sqliteStoreRoundTripsSnapshotScopedLinksAndDirectQueries() {
        Path database = tempDir.resolve("traceability-roundtrip.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            verifyRoundTripAndQueries(snapshots, traceability);
        }
    }

    @Test
    void memoryStoreRejectsUnknownSnapshotAndLinkIdentityMutation() {
        var snapshots = new MemorySpecificationKnowledgeStore();
        verifyOwnershipAndCollisionRules(snapshots, new MemoryTraceabilityStore(snapshots));
    }

    @Test
    void sqliteStoreRejectsUnknownSnapshotAndLinkIdentityMutation() {
        Path database = tempDir.resolve("traceability-rules.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            verifyOwnershipAndCollisionRules(snapshots, traceability);
        }
    }

    @Test
    void sqliteReopenPreservesDefinitionsEvidenceAndSnapshotMembership() {
        Path database = tempDir.resolve("traceability-reopen.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId firstSnapshot = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId secondSnapshot = KnowledgeSnapshotId.generate();
        TraceabilityLink link = link(
                TraceabilityLinkId.generate(),
                new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, DomainIdentity.generate()),
                TraceabilityRelationType.AFFECTS,
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, DomainIdentity.generate()),
                Set.of(EvidenceId.generate(), EvidenceId.generate()),
                T0);

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            createProjectAndSnapshots(snapshots, projectId, firstSnapshot, secondSnapshot);
            traceability.putLink(firstSnapshot, link);
            traceability.putLink(secondSnapshot, link);
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            assertTrue(snapshots.findSnapshot(firstSnapshot).isPresent());
            assertEquals(link, traceability.findLink(firstSnapshot, link.id()).orElseThrow());
            assertEquals(link, traceability.findLink(secondSnapshot, link.id()).orElseThrow());
            assertEquals(List.of(link), traceability.outgoing(firstSnapshot, link.source(), Set.of()));
            assertEquals(List.of(link), traceability.incoming(secondSnapshot, link.target(), Set.of()));
        }
    }

    private void verifyRoundTripAndQueries(
            SpecificationKnowledgeStore snapshots,
            TraceabilityStore traceability) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId firstSnapshot = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId secondSnapshot = KnowledgeSnapshotId.generate();
        createProjectAndSnapshots(snapshots, projectId, firstSnapshot, secondSnapshot);

        TraceabilityEntityRef change = new TraceabilityEntityRef(
                TraceabilityEntityKind.CHANGE, DomainIdentity.generate());
        TraceabilityEntityRef requirement = new TraceabilityEntityRef(
                TraceabilityEntityKind.REQUIREMENT, DomainIdentity.generate());
        TraceabilityEntityRef decision = new TraceabilityEntityRef(
                TraceabilityEntityKind.DESIGN_DECISION, DomainIdentity.generate());

        TraceabilityLink affects = link(
                TraceabilityLinkId.generate(), change, TraceabilityRelationType.AFFECTS,
                requirement, Set.of(EvidenceId.generate()), T0);
        TraceabilityLink decidedBy = link(
                TraceabilityLinkId.generate(), change, TraceabilityRelationType.DECIDED_BY,
                decision, Set.of(EvidenceId.generate()), T0.plusSeconds(1));

        traceability.putLink(firstSnapshot, affects);
        traceability.putLink(firstSnapshot, decidedBy);
        traceability.putLink(firstSnapshot, affects); // idempotent membership
        traceability.putLink(secondSnapshot, affects); // same immutable definition, second membership

        assertEquals(affects, traceability.findLink(firstSnapshot, affects.id()).orElseThrow());
        assertEquals(affects, traceability.findLink(secondSnapshot, affects.id()).orElseThrow());
        assertTrue(traceability.findLink(secondSnapshot, decidedBy.id()).isEmpty());

        List<TraceabilityLink> expectedOutgoing = List.of(affects, decidedBy).stream()
                .sorted(Comparator.comparing(TraceabilityLink::id))
                .toList();
        assertEquals(expectedOutgoing, traceability.outgoing(firstSnapshot, change, Set.of()));
        assertEquals(List.of(affects), traceability.outgoing(
                firstSnapshot, change, Set.of(TraceabilityRelationType.AFFECTS)));
        assertEquals(List.of(affects), traceability.incoming(firstSnapshot, requirement, Set.of()));
        assertEquals(List.of(), traceability.incoming(secondSnapshot, decision, Set.of()));
    }

    private void verifyOwnershipAndCollisionRules(
            SpecificationKnowledgeStore snapshots,
            TraceabilityStore traceability) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId knownSnapshot = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId anotherSnapshot = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId unknownSnapshot = KnowledgeSnapshotId.generate();
        createProjectAndSnapshots(snapshots, projectId, knownSnapshot, anotherSnapshot);

        TraceabilityEntityRef source = new TraceabilityEntityRef(
                TraceabilityEntityKind.SCENARIO, DomainIdentity.generate());
        TraceabilityEntityRef target = new TraceabilityEntityRef(
                TraceabilityEntityKind.REQUIREMENT, DomainIdentity.generate());
        TraceabilityLinkId linkId = TraceabilityLinkId.generate();
        TraceabilityLink original = link(
                linkId, source, TraceabilityRelationType.REFINES,
                target, Set.of(EvidenceId.generate()), T0);
        TraceabilityLink mutation = link(
                linkId, source, TraceabilityRelationType.RELATED_TO,
                target, original.evidenceIds(), T0);

        assertThrows(KnowledgeStoreException.class, () -> traceability.putLink(unknownSnapshot, original));
        assertThrows(KnowledgeStoreException.class, () -> traceability.findLink(unknownSnapshot, linkId));

        traceability.putLink(knownSnapshot, original);
        traceability.putLink(anotherSnapshot, original);
        assertThrows(KnowledgeStoreException.class, () -> traceability.putLink(knownSnapshot, mutation));
        assertThrows(KnowledgeStoreException.class, () -> traceability.putLink(anotherSnapshot, mutation));

        assertEquals(original, traceability.findLink(knownSnapshot, linkId).orElseThrow());
        assertEquals(original, traceability.findLink(anotherSnapshot, linkId).orElseThrow());
    }

    private void createProjectAndSnapshots(
            SpecificationKnowledgeStore snapshots,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId firstSnapshot,
            KnowledgeSnapshotId secondSnapshot) {
        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        snapshots.putSnapshot(snapshot(firstSnapshot, projectId, "revision-1"));
        snapshots.putSnapshot(snapshot(secondSnapshot, projectId, "revision-2"));
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

    private TraceabilityLink link(
            TraceabilityLinkId id,
            TraceabilityEntityRef source,
            TraceabilityRelationType relationType,
            TraceabilityEntityRef target,
            Set<EvidenceId> evidence,
            Instant observedAt) {
        return new TraceabilityLink(
                id,
                source,
                relationType,
                target,
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                evidence,
                observedAt);
    }
}
