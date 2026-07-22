package com.morpheus.application.snapshot;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotConflictException;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotLifecycleServiceTest {
    private static final Instant T0 = Instant.parse("2026-07-22T12:00:00Z");

    @Test
    void publishesReadySnapshotAndRetiresPreviousActiveAtomically() {
        Fixture fixture = new Fixture();
        KnowledgeSnapshotMetadata first = fixture.building(Optional.empty(), "revision-1", T0);
        fixture.service.registerBuilding(first);
        assertEquals(KnowledgeSnapshotState.READY, fixture.service.validate(first.id(), ignored -> SnapshotValidationResult.valid()).state());
        assertEquals(KnowledgeSnapshotState.ACTIVE, fixture.service.activate(first.id()).state());

        KnowledgeSnapshotMetadata second = fixture.building(Optional.of(first.id()), "revision-2", T0.plusSeconds(1));
        fixture.service.registerBuilding(second);
        fixture.service.validate(second.id(), ignored -> SnapshotValidationResult.validWithWarnings(List.of("non-fatal warning")));

        assertEquals(first.id(), fixture.service.current(fixture.projectId).orElseThrow().id());
        fixture.service.activate(second.id());
        assertEquals(second.id(), fixture.service.current(fixture.projectId).orElseThrow().id());
        assertEquals(KnowledgeSnapshotState.RETIRED, fixture.store.findSnapshot(first.id()).orElseThrow().state());
    }

    @Test
    void invalidValidationMarksCandidateFailedAndKeepsPreviousActive() {
        Fixture fixture = activeFixture();
        KnowledgeSnapshotMetadata current = fixture.service.current(fixture.projectId).orElseThrow();
        KnowledgeSnapshotMetadata candidate = fixture.building(Optional.of(current.id()), "invalid", T0.plusSeconds(2));
        fixture.service.registerBuilding(candidate);

        KnowledgeSnapshotMetadata failed = fixture.service.validate(
                candidate.id(),
                ignored -> SnapshotValidationResult.invalid(List.of("broken internal reference")));

        assertEquals(KnowledgeSnapshotState.FAILED, failed.state());
        assertEquals(current.id(), fixture.service.current(fixture.projectId).orElseThrow().id());
    }

    @Test
    void validatorExceptionMarksCandidateFailedAndKeepsPreviousActive() {
        Fixture fixture = activeFixture();
        KnowledgeSnapshotMetadata current = fixture.service.current(fixture.projectId).orElseThrow();
        KnowledgeSnapshotMetadata candidate = fixture.building(Optional.of(current.id()), "throws", T0.plusSeconds(2));
        fixture.service.registerBuilding(candidate);

        assertThrows(SnapshotValidationException.class, () -> fixture.service.validate(candidate.id(), ignored -> {
            throw new IllegalStateException("validator crashed");
        }));

        assertEquals(KnowledgeSnapshotState.FAILED, fixture.store.findSnapshot(candidate.id()).orElseThrow().state());
        assertEquals(current.id(), fixture.service.current(fixture.projectId).orElseThrow().id());
    }

    @Test
    void nonActiveSnapshotStatesAreNeverReturnedAsCurrent() {
        Fixture fixture = new Fixture();
        KnowledgeSnapshotMetadata building = fixture.building(Optional.empty(), "building", T0);
        fixture.service.registerBuilding(building);
        assertTrue(fixture.service.current(fixture.projectId).isEmpty());

        fixture.store.transitionSnapshotState(building.id(), KnowledgeSnapshotState.BUILDING, KnowledgeSnapshotState.VALIDATING);
        assertTrue(fixture.service.current(fixture.projectId).isEmpty());

        fixture.store.transitionSnapshotState(building.id(), KnowledgeSnapshotState.VALIDATING, KnowledgeSnapshotState.READY);
        assertTrue(fixture.service.current(fixture.projectId).isEmpty());

        Fixture failedFixture = new Fixture();
        KnowledgeSnapshotMetadata failed = failedFixture.building(Optional.empty(), "failed", T0);
        failedFixture.service.registerBuilding(failed);
        failedFixture.service.validate(failed.id(), ignored -> SnapshotValidationResult.invalid(List.of("invalid")));
        assertTrue(failedFixture.service.current(failedFixture.projectId).isEmpty());
    }

    @Test
    void stalePredecessorCannotReplaceNewerActiveSnapshot() {
        Fixture fixture = activeFixture();
        KnowledgeSnapshotMetadata first = fixture.service.current(fixture.projectId).orElseThrow();

        KnowledgeSnapshotMetadata second = fixture.building(Optional.of(first.id()), "revision-2", T0.plusSeconds(1));
        fixture.service.registerBuilding(second);
        fixture.service.validate(second.id(), ignored -> SnapshotValidationResult.valid());
        fixture.service.activate(second.id());

        KnowledgeSnapshotMetadata stale = fixture.building(Optional.of(first.id()), "stale", T0.plusSeconds(2));
        fixture.service.registerBuilding(stale);
        fixture.service.validate(stale.id(), ignored -> SnapshotValidationResult.valid());

        assertThrows(SnapshotConflictException.class, () -> fixture.service.activate(stale.id()));
        assertEquals(second.id(), fixture.service.current(fixture.projectId).orElseThrow().id());
        assertEquals(KnowledgeSnapshotState.READY, fixture.store.findSnapshot(stale.id()).orElseThrow().state());
    }

    @Test
    void activationBeforeReadyIsRejected() {
        Fixture fixture = new Fixture();
        KnowledgeSnapshotMetadata building = fixture.building(Optional.empty(), "revision-1", T0);
        fixture.service.registerBuilding(building);

        assertThrows(SnapshotConflictException.class, () -> fixture.service.activate(building.id()));
        assertTrue(fixture.service.current(fixture.projectId).isEmpty());
    }

    @Test
    void candidatesMustStartInBuilding() {
        Fixture fixture = new Fixture();
        KnowledgeSnapshotMetadata ready = new KnowledgeSnapshotMetadata(
                KnowledgeSnapshotId.generate(),
                fixture.projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("ready"),
                T0);

        assertThrows(SnapshotConflictException.class, () -> fixture.service.registerBuilding(ready));
    }

    private Fixture activeFixture() {
        Fixture fixture = new Fixture();
        KnowledgeSnapshotMetadata first = fixture.building(Optional.empty(), "revision-1", T0);
        fixture.service.registerBuilding(first);
        fixture.service.validate(first.id(), ignored -> SnapshotValidationResult.valid());
        fixture.service.activate(first.id());
        return fixture;
    }

    private static final class Fixture {
        private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        private final MemorySpecificationKnowledgeStore store = new MemorySpecificationKnowledgeStore();
        private final SnapshotLifecycleService service = new SnapshotLifecycleService(store);

        private Fixture() {
            store.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        }

        private KnowledgeSnapshotMetadata building(
                Optional<KnowledgeSnapshotId> predecessor,
                String revision,
                Instant createdAt) {
            return new KnowledgeSnapshotMetadata(
                    KnowledgeSnapshotId.generate(),
                    projectId,
                    predecessor,
                    KnowledgeSnapshotState.BUILDING,
                    Optional.of(revision),
                    createdAt);
        }
    }
}
