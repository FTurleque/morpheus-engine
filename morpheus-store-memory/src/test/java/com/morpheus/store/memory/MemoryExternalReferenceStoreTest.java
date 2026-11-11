package com.morpheus.store.memory;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryExternalReferenceStoreTest {
    private static final Instant T0 = Instant.parse("2026-10-30T09:00:00Z");
    private static final ProjectSpecificationId PROJECT = new ProjectSpecificationId(identity(1));
    private static final KnowledgeSnapshotId SNAPSHOT = new KnowledgeSnapshotId(identity(2));
    private static final KnowledgeSnapshotId OTHER_SNAPSHOT = new KnowledgeSnapshotId(identity(3));
    private static final KnowledgeSnapshotId UNKNOWN_SNAPSHOT = new KnowledgeSnapshotId(identity(4));
    private static final DomainIdentity OWNER = identity(10);
    private static final DomainIdentity OTHER_OWNER = identity(11);

    @Test
    void aStoredReferenceIsFoundInItsSnapshotOnly() {
        MemoryExternalReferenceStore store = new MemoryExternalReferenceStore(snapshots());
        ExternalReference reference = reference(20, OWNER, "symbol:A");

        store.putReference(SNAPSHOT, reference);

        assertEquals(Optional.of(reference), store.findReference(SNAPSHOT, reference.id()));
        assertEquals(Optional.empty(), store.findReference(OTHER_SNAPSHOT, reference.id()));
        assertEquals(Optional.empty(), store.findReference(SNAPSHOT, new ExternalReferenceId(identity(99))));
        assertTrue(store.findByOwner(OTHER_SNAPSHOT, OWNER).isEmpty());
    }

    @Test
    void referencesOfAnOwnerAreListedInIdentityOrderWithoutTheOtherOwners() {
        MemoryExternalReferenceStore store = new MemoryExternalReferenceStore(snapshots());
        ExternalReference late = reference(23, OWNER, "symbol:C");
        ExternalReference early = reference(21, OWNER, "symbol:A");
        ExternalReference foreign = reference(22, OTHER_OWNER, "symbol:B");
        List.of(late, foreign, early).forEach(item -> store.putReference(SNAPSHOT, item));

        assertEquals(List.of(early, late), store.findByOwner(SNAPSHOT, OWNER));
        assertEquals(List.of(foreign), store.findByOwner(SNAPSHOT, OTHER_OWNER));
    }

    @Test
    void anIdenticalReferenceIsAcceptedTwiceButADifferentOneUnderTheSameIdentityIsAConflict() {
        MemoryExternalReferenceStore store = new MemoryExternalReferenceStore(snapshots());
        ExternalReference reference = reference(30, OWNER, "symbol:A");
        store.putReference(SNAPSHOT, reference);
        store.putReference(SNAPSHOT, reference);

        ExternalReference rewritten = reference.transition(
                ExternalReferenceResolutionState.UNRESOLVED,
                ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                Optional.empty(),
                T0);
        KnowledgeStoreException collision = assertThrows(KnowledgeStoreException.class,
                () -> store.putReference(SNAPSHOT, rewritten));

        assertEquals("external reference identity collision in snapshot: " + reference.id(), collision.getMessage());
        assertEquals(Optional.of(reference), store.findReference(SNAPSHOT, reference.id()));
        assertEquals(List.of(reference), store.findByOwner(SNAPSHOT, OWNER));
    }

    @Test
    void theSameIdentityMayCarryADifferentReferenceInAnotherSnapshot() {
        MemoryExternalReferenceStore store = new MemoryExternalReferenceStore(snapshots());
        ExternalReference reference = reference(40, OWNER, "symbol:A");
        ExternalReference sameIdElsewhere = reference(40, OTHER_OWNER, "symbol:Z");

        store.putReference(SNAPSHOT, reference);
        store.putReference(OTHER_SNAPSHOT, sameIdElsewhere);

        assertEquals(Optional.of(reference), store.findReference(SNAPSHOT, reference.id()));
        assertEquals(Optional.of(sameIdElsewhere), store.findReference(OTHER_SNAPSHOT, reference.id()));
    }

    @Test
    void everyOperationOnAnUnknownSnapshotIsRefusedByName() {
        MemoryExternalReferenceStore store = new MemoryExternalReferenceStore(snapshots());
        ExternalReference reference = reference(50, OWNER, "symbol:A");
        String expected = "snapshot not found for external reference: " + UNKNOWN_SNAPSHOT;

        KnowledgeStoreException put = assertThrows(KnowledgeStoreException.class,
                () -> store.putReference(UNKNOWN_SNAPSHOT, reference));
        KnowledgeStoreException find = assertThrows(KnowledgeStoreException.class,
                () -> store.findReference(UNKNOWN_SNAPSHOT, reference.id()));
        KnowledgeStoreException byOwner = assertThrows(KnowledgeStoreException.class,
                () -> store.findByOwner(UNKNOWN_SNAPSHOT, OWNER));

        assertEquals(expected, put.getMessage());
        assertEquals(expected, find.getMessage());
        assertEquals(expected, byOwner.getMessage());
    }

    private static MemorySpecificationKnowledgeStore snapshots() {
        MemorySpecificationKnowledgeStore store = new MemorySpecificationKnowledgeStore();
        store.putProject(new ProjectStoreEntry(PROJECT, SourceLocator.file("workspace-references")));
        for (KnowledgeSnapshotId snapshotId : List.of(SNAPSHOT, OTHER_SNAPSHOT)) {
            store.putSnapshot(new KnowledgeSnapshotMetadata(snapshotId, PROJECT, Optional.empty(),
                    KnowledgeSnapshotState.READY, Optional.of("revision-1"), T0));
        }
        return store;
    }

    private static ExternalReference reference(int number, DomainIdentity owner, String externalId) {
        return ExternalReference.unvalidated(
                new ExternalReferenceId(identity(number)),
                owner,
                new ExternalReferenceTarget("minos", Optional.of("morpheus-engine"), "symbol", externalId,
                        Optional.empty()),
                Optional.empty());
    }

    private static DomainIdentity identity(int number) {
        return DomainIdentity.parse("00000000-0000-7000-8000-%012d".formatted(number));
    }
}
