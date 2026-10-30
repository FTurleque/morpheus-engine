package com.morpheus.application.snapshot;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotConflictException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A validation outcome decides READY or FAILED; a validator that breaks fails the candidate and is named. */
class SnapshotValidationTest {
    private static final Instant T0 = Instant.parse("2026-10-30T12:00:00Z");

    private final Store store = new Store();
    private final SnapshotLifecycleService lifecycle = new SnapshotLifecycleService(store);
    private final KnowledgeSnapshotMetadata candidate = new KnowledgeSnapshotMetadata(
            KnowledgeSnapshotId.generate(), ProjectSpecificationId.generate(), Optional.empty(),
            KnowledgeSnapshotState.BUILDING, Optional.of("rev"), T0);

    @Test
    void aResultIsValidExactlyWhenItCarriesNoErrorAndItsItemsAreTrimmed() {
        assertTrue(SnapshotValidationResult.valid().isValid());

        SnapshotValidationResult warned = SnapshotValidationResult.validWithWarnings(List.of("  slow source  "));
        assertTrue(warned.isValid(), "a warning does not invalidate a snapshot");
        assertEquals(List.of("slow source"), warned.warnings());

        SnapshotValidationResult invalid = SnapshotValidationResult.invalid(List.of(" dangling link "));
        assertFalse(invalid.isValid());
        assertEquals(List.of("dangling link"), invalid.errors());
        assertTrue(invalid.warnings().isEmpty());
    }

    @Test
    void anInvalidResultWithoutAnErrorAndABlankItemAreRefusedByName() {
        IllegalArgumentException noError = assertThrows(IllegalArgumentException.class,
                () -> SnapshotValidationResult.invalid(List.of()));
        assertEquals("invalid validation result requires at least one error", noError.getMessage());

        IllegalArgumentException blankWarning = assertThrows(IllegalArgumentException.class,
                () -> SnapshotValidationResult.validWithWarnings(List.of("   ")));
        assertEquals("warnings item must not be blank", blankWarning.getMessage());

        NullPointerException missingItem = assertThrows(NullPointerException.class,
                () -> new SnapshotValidationResult(Arrays.asList("ok", null), List.of()));
        assertEquals("errors item", missingItem.getMessage());
    }

    @Test
    void aValidCandidateBecomesReadyThroughValidating() {
        lifecycle.registerBuilding(candidate);

        KnowledgeSnapshotMetadata ready = lifecycle.validate(candidate.id(), snapshot -> {
            assertEquals(KnowledgeSnapshotState.VALIDATING, snapshot.state(), "the validator sees the VALIDATING state");
            return SnapshotValidationResult.validWithWarnings(List.of("advisory"));
        });

        assertEquals(KnowledgeSnapshotState.READY, ready.state());
        assertEquals(List.of(
                        KnowledgeSnapshotState.BUILDING + "->" + KnowledgeSnapshotState.VALIDATING,
                        KnowledgeSnapshotState.VALIDATING + "->" + KnowledgeSnapshotState.READY),
                store.transitions);
    }

    @Test
    void anInvalidCandidateBecomesFailed() {
        lifecycle.registerBuilding(candidate);

        KnowledgeSnapshotMetadata failed = lifecycle.validate(
                candidate.id(), snapshot -> SnapshotValidationResult.invalid(List.of("missing requirement")));

        assertEquals(KnowledgeSnapshotState.FAILED, failed.state());
    }

    @Test
    void aValidatorThatBreaksFailsTheCandidateAndIsReportedWithItsCause() {
        lifecycle.registerBuilding(candidate);
        IllegalStateException broken = new IllegalStateException("synthetic validator failure");

        SnapshotValidationException thrown = assertThrows(SnapshotValidationException.class,
                () -> lifecycle.validate(candidate.id(), snapshot -> {
                    throw broken;
                }));

        assertEquals("Snapshot validation failed unexpectedly: " + candidate.id(), thrown.getMessage());
        assertSame(broken, thrown.getCause());
        assertEquals(KnowledgeSnapshotState.FAILED, store.snapshots.get(candidate.id()).state(),
                "a candidate whose validation broke is not left in VALIDATING");
    }

    @Test
    void aMissingValidatorResultIsAValidationFailureAndAFailedMarkingIsKeptAsSuppressed() {
        lifecycle.registerBuilding(candidate);
        store.refuseFailedMarking = true;

        SnapshotValidationException thrown = assertThrows(SnapshotValidationException.class,
                () -> lifecycle.validate(candidate.id(), snapshot -> null));

        NullPointerException cause = (NullPointerException) thrown.getCause();
        assertEquals("validator result", cause.getMessage());
        assertEquals(1, cause.getSuppressed().length);
        assertTrue(cause.getSuppressed()[0] instanceof SnapshotConflictException);
    }

    @Test
    void aCandidateThatDoesNotStartInBuildingIsAConflict() {
        SnapshotConflictException refused = assertThrows(SnapshotConflictException.class,
                () -> lifecycle.registerBuilding(candidate.withState(KnowledgeSnapshotState.READY)));

        assertEquals("new snapshot candidates must start in BUILDING", refused.getMessage());
        assertTrue(store.snapshots.isEmpty(), "a refused candidate is not stored");
    }

    private static final class Store implements SpecificationKnowledgeStore {
        private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> snapshots = new LinkedHashMap<>();
        private final List<String> transitions = new ArrayList<>();
        private boolean refuseFailedMarking;

        @Override public void putSnapshot(KnowledgeSnapshotMetadata snapshot) {
            snapshots.put(snapshot.id(), snapshot);
        }

        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId id) {
            return Optional.ofNullable(snapshots.get(id));
        }

        @Override public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId id, KnowledgeSnapshotState from, KnowledgeSnapshotState to) {
            KnowledgeSnapshotMetadata current = snapshots.get(id);
            if (current.state() != from || (refuseFailedMarking && to == KnowledgeSnapshotState.FAILED)) {
                throw new SnapshotConflictException("unexpected snapshot state: " + current.state());
            }
            transitions.add(from + "->" + to);
            KnowledgeSnapshotMetadata updated = current.withState(to);
            snapshots.put(id, updated);
            return updated;
        }

        @Override public void putProject(ProjectStoreEntry project) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId id) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { throw new UnsupportedOperationException(); }
        @Override public List<ProjectStoreEntry> listProjects() { throw new UnsupportedOperationException(); }
        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId id) { throw new UnsupportedOperationException(); }
        @Override public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId id, Optional<KnowledgeSnapshotId> expected) {
            throw new UnsupportedOperationException();
        }
    }
}
