package com.morpheus.store.memory;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryTraceabilityStoreTest {
    private static final Instant T0 = Instant.parse("2026-10-30T09:00:00Z");
    private static final ProjectSpecificationId PROJECT = new ProjectSpecificationId(identity(1));
    private static final KnowledgeSnapshotId SNAPSHOT = new KnowledgeSnapshotId(identity(2));
    private static final KnowledgeSnapshotId OTHER_SNAPSHOT = new KnowledgeSnapshotId(identity(3));
    private static final KnowledgeSnapshotId UNKNOWN_SNAPSHOT = new KnowledgeSnapshotId(identity(4));
    private static final TraceabilityEntityRef TASK = ref(TraceabilityEntityKind.IMPLEMENTATION_TASK, 10);
    private static final TraceabilityEntityRef REQUIREMENT = ref(TraceabilityEntityKind.REQUIREMENT, 11);
    private static final TraceabilityEntityRef CONSTRAINT = ref(TraceabilityEntityKind.CONSTRAINT, 12);

    @Test
    void aLinkIsFoundOnlyInTheSnapshotsItWasPutIn() {
        MemoryTraceabilityStore store = new MemoryTraceabilityStore(snapshots());
        TraceabilityLink link = link(20, TASK, TraceabilityRelationType.IMPLEMENTS, REQUIREMENT);

        store.putLink(SNAPSHOT, link);

        assertEquals(Optional.of(link), store.findLink(SNAPSHOT, link.id()));
        assertEquals(Optional.empty(), store.findLink(OTHER_SNAPSHOT, link.id()));
        assertTrue(store.outgoing(OTHER_SNAPSHOT, TASK, Set.of()).isEmpty());

        store.putLink(OTHER_SNAPSHOT, link);
        assertEquals(Optional.of(link), store.findLink(OTHER_SNAPSHOT, link.id()));
    }

    @Test
    void outgoingAndIncomingLinksAreFilteredByRelationAndOrderedByIdentity() {
        MemoryTraceabilityStore store = new MemoryTraceabilityStore(snapshots());
        TraceabilityLink implementsLate = link(33, TASK, TraceabilityRelationType.IMPLEMENTS, REQUIREMENT);
        TraceabilityLink implementsEarly = link(31, TASK, TraceabilityRelationType.IMPLEMENTS, CONSTRAINT);
        TraceabilityLink constrains = link(32, CONSTRAINT, TraceabilityRelationType.CONSTRAINS, REQUIREMENT);
        store.putLinks(SNAPSHOT, List.of(implementsLate, constrains, implementsEarly));

        assertEquals(List.of(implementsEarly, implementsLate), store.outgoing(SNAPSHOT, TASK, Set.of()));
        assertEquals(List.of(implementsEarly, implementsLate),
                store.outgoing(SNAPSHOT, TASK, Set.of(TraceabilityRelationType.IMPLEMENTS)));
        assertTrue(store.outgoing(SNAPSHOT, TASK, Set.of(TraceabilityRelationType.CONSTRAINS)).isEmpty());
        assertEquals(List.of(constrains, implementsLate), store.incoming(SNAPSHOT, REQUIREMENT, Set.of()));
        assertEquals(List.of(constrains),
                store.incoming(SNAPSHOT, REQUIREMENT, Set.of(TraceabilityRelationType.CONSTRAINS)));
    }

    @Test
    void anIdenticalLinkIsAcceptedTwiceButADifferentOneUnderTheSameIdentityIsAConflict() {
        MemoryTraceabilityStore store = new MemoryTraceabilityStore(snapshots());
        TraceabilityLink link = link(40, TASK, TraceabilityRelationType.IMPLEMENTS, REQUIREMENT);
        store.putLink(SNAPSHOT, link);
        store.putLink(SNAPSHOT, link);

        TraceabilityLink rewritten = link(40, TASK, TraceabilityRelationType.IMPLEMENTS, CONSTRAINT);
        KnowledgeStoreException collision = assertThrows(KnowledgeStoreException.class,
                () -> store.putLink(OTHER_SNAPSHOT, rewritten));

        assertEquals("traceability link identity collision: " + link.id(), collision.getMessage());
        assertEquals(Optional.of(link), store.findLink(SNAPSHOT, link.id()));
        assertEquals(Optional.empty(), store.findLink(OTHER_SNAPSHOT, link.id()));
        assertEquals(List.of(link), store.outgoing(SNAPSHOT, TASK, Set.of()));
    }

    @Test
    void aBatchWithAConflictLeavesNothingBehind() {
        MemoryTraceabilityStore store = new MemoryTraceabilityStore(snapshots());
        TraceabilityLink stored = link(50, TASK, TraceabilityRelationType.IMPLEMENTS, REQUIREMENT);
        store.putLink(SNAPSHOT, stored);
        TraceabilityLink fresh = link(51, CONSTRAINT, TraceabilityRelationType.CONSTRAINS, REQUIREMENT);
        TraceabilityLink againstStored = link(50, TASK, TraceabilityRelationType.IMPLEMENTS, CONSTRAINT);

        KnowledgeStoreException collision = assertThrows(KnowledgeStoreException.class,
                () -> store.putLinks(OTHER_SNAPSHOT, List.of(fresh, againstStored)));

        assertEquals("traceability link identity collision: " + stored.id(), collision.getMessage());
        assertEquals(Optional.empty(), store.findLink(OTHER_SNAPSHOT, fresh.id()));
        assertEquals(Optional.empty(), store.findLink(SNAPSHOT, fresh.id()));
        assertTrue(store.incoming(OTHER_SNAPSHOT, REQUIREMENT, Set.of()).isEmpty());
    }

    @Test
    void aBatchThatContradictsItselfIsRefusedAndAConsistentRepeatIsAccepted() {
        MemoryTraceabilityStore store = new MemoryTraceabilityStore(snapshots());
        TraceabilityLink first = link(60, TASK, TraceabilityRelationType.IMPLEMENTS, REQUIREMENT);
        TraceabilityLink contradiction = link(60, TASK, TraceabilityRelationType.IMPLEMENTS, CONSTRAINT);

        KnowledgeStoreException collision = assertThrows(KnowledgeStoreException.class,
                () -> store.putLinks(SNAPSHOT, List.of(first, contradiction)));
        assertEquals("traceability link identity collision: " + first.id(), collision.getMessage());
        assertEquals(Optional.empty(), store.findLink(SNAPSHOT, first.id()));

        store.putLinks(SNAPSHOT, List.of(first, first));
        assertEquals(List.of(first), store.outgoing(SNAPSHOT, TASK, Set.of()));
    }

    @Test
    void anEmptyBatchStoresNothingAndDoesNotEvenLookTheSnapshotUp() {
        MemoryTraceabilityStore store = new MemoryTraceabilityStore(snapshots());

        store.putLinks(UNKNOWN_SNAPSHOT, List.of());

        assertTrue(store.outgoing(SNAPSHOT, TASK, Set.of()).isEmpty());
    }

    @Test
    void everyOperationOnAnUnknownSnapshotIsRefusedByName() {
        MemoryTraceabilityStore store = new MemoryTraceabilityStore(snapshots());
        TraceabilityLink link = link(70, TASK, TraceabilityRelationType.IMPLEMENTS, REQUIREMENT);
        String expected = "snapshot not found for traceability link: " + UNKNOWN_SNAPSHOT;

        List<KnowledgeStoreException> refusals = List.of(
                assertThrows(KnowledgeStoreException.class, () -> store.putLink(UNKNOWN_SNAPSHOT, link)),
                assertThrows(KnowledgeStoreException.class, () -> store.putLinks(UNKNOWN_SNAPSHOT, List.of(link))),
                assertThrows(KnowledgeStoreException.class, () -> store.findLink(UNKNOWN_SNAPSHOT, link.id())),
                assertThrows(KnowledgeStoreException.class, () -> store.outgoing(UNKNOWN_SNAPSHOT, TASK, Set.of())),
                assertThrows(KnowledgeStoreException.class,
                        () -> store.incoming(UNKNOWN_SNAPSHOT, REQUIREMENT, Set.of())));

        refusals.forEach(refusal -> assertEquals(expected, refusal.getMessage()));
    }

    private static MemorySpecificationKnowledgeStore snapshots() {
        MemorySpecificationKnowledgeStore store = new MemorySpecificationKnowledgeStore();
        store.putProject(new ProjectStoreEntry(PROJECT, SourceLocator.file("workspace-traceability")));
        for (KnowledgeSnapshotId snapshotId : List.of(SNAPSHOT, OTHER_SNAPSHOT)) {
            store.putSnapshot(new KnowledgeSnapshotMetadata(snapshotId, PROJECT, Optional.empty(),
                    KnowledgeSnapshotState.READY, Optional.of("revision-1"), T0));
        }
        return store;
    }

    private static TraceabilityLink link(
            int number,
            TraceabilityEntityRef source,
            TraceabilityRelationType relation,
            TraceabilityEntityRef target) {
        return new TraceabilityLink(new TraceabilityLinkId(identity(number)), source, relation, target,
                TraceabilityLinkOrigin.EXPLICIT, TraceabilityResolutionState.RESOLVED, Optional.empty(),
                Set.of(new EvidenceId(identity(99))), T0);
    }

    private static TraceabilityEntityRef ref(TraceabilityEntityKind kind, int number) {
        return new TraceabilityEntityRef(kind, identity(number));
    }

    private static DomainIdentity identity(int number) {
        return DomainIdentity.parse("00000000-0000-7000-8000-%012d".formatted(number));
    }
}
