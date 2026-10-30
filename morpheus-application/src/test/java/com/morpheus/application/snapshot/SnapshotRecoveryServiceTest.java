package com.morpheus.application.snapshot;

import com.morpheus.application.operability.OperationalEvent;
import com.morpheus.application.operability.OperationalEventCode;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotConflictException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stale technical candidates are failed, fresh ones and published history are left alone, and races are counted. */
class SnapshotRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final Store store = new Store();
    private final OperationalMetrics metrics = new OperationalMetrics();
    private final List<OperationalEvent> events = new ArrayList<>();
    private final OperationalRecorder recorder = new OperationalRecorder(events::add, metrics);

    @Test
    void onlyTechnicalCandidatesAtOrBeforeTheCutoffAreFailed() {
        Instant cutoff = NOW.minusSeconds(600);
        KnowledgeSnapshotMetadata staleBuilding = snapshot(KnowledgeSnapshotState.BUILDING, cutoff.minusSeconds(1));
        KnowledgeSnapshotMetadata validatingAtCutoff = snapshot(KnowledgeSnapshotState.VALIDATING, cutoff);
        KnowledgeSnapshotMetadata freshBuilding = snapshot(KnowledgeSnapshotState.BUILDING, cutoff.plusSeconds(1));
        KnowledgeSnapshotMetadata oldActive = snapshot(KnowledgeSnapshotState.ACTIVE, cutoff.minusSeconds(3600));
        KnowledgeSnapshotMetadata oldReady = snapshot(KnowledgeSnapshotState.READY, cutoff.minusSeconds(3600));

        SnapshotRecoveryService.RecoveryReport report = new SnapshotRecoveryService(store, recorder)
                .recoverStaleCandidates(projectId, cutoff);

        assertEquals(Set.of(staleBuilding.id(), validatingAtCutoff.id()), Set.copyOf(report.recoveredCandidates()));
        assertTrue(report.racedCandidates().isEmpty(), "no candidate moved under the recovery");
        assertEquals(KnowledgeSnapshotState.FAILED, store.state(staleBuilding.id()));
        assertEquals(KnowledgeSnapshotState.FAILED, store.state(validatingAtCutoff.id()));
        assertEquals(KnowledgeSnapshotState.BUILDING, store.state(freshBuilding.id()),
                "a fresh candidate may belong to a concurrent process");
        assertEquals(KnowledgeSnapshotState.ACTIVE, store.state(oldActive.id()), "published history is not touched");
        assertEquals(KnowledgeSnapshotState.READY, store.state(oldReady.id()));
        assertEquals(2L, metrics.snapshot().counters().get("snapshot.recovery.candidate_failed"));
        assertEquals(1L, metrics.snapshot().counters().get("snapshot.recovery.success"));

        assertEquals(OperationalEventCode.RECOVERY_STARTED, events.getFirst().code());
        assertEquals(cutoff.toString(), events.getFirst().attributes().get("staleAtOrBefore"));
        OperationalEvent completed = events.getLast();
        assertEquals(OperationalEventCode.RECOVERY_COMPLETED, completed.code());
        assertEquals("2", completed.attributes().get("recoveredCount"));
        assertEquals("0", completed.attributes().get("raceCount"));
    }

    @Test
    void aCandidateThatMovedUnderTheRecoveryIsReportedAsARaceAndLeftAsItIs() {
        KnowledgeSnapshotMetadata raced = snapshot(KnowledgeSnapshotState.BUILDING, NOW.minusSeconds(3600));
        KnowledgeSnapshotMetadata recovered = snapshot(KnowledgeSnapshotState.VALIDATING, NOW.minusSeconds(3600));
        store.racing.add(raced.id());

        SnapshotRecoveryService.RecoveryReport report = new SnapshotRecoveryService(store, recorder)
                .recoverStaleCandidates(projectId, NOW);

        assertEquals(List.of(recovered.id()), report.recoveredCandidates());
        assertEquals(List.of(raced.id()), report.racedCandidates());
        assertEquals(KnowledgeSnapshotState.BUILDING, store.state(raced.id()));
        assertEquals(1L, metrics.snapshot().counters().get("snapshot.recovery.race"));
        assertEquals("1", events.getLast().attributes().get("raceCount"));
    }

    @Test
    void aStoreFailureEndsTheOperationAsFailedAndIsRethrownUnchanged() {
        IllegalStateException listing = new IllegalStateException("synthetic listing failure");
        store.listingFailure = listing;

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new SnapshotRecoveryService(store, recorder).recoverStaleCandidates(projectId, NOW));

        assertSame(listing, thrown);
        OperationalEvent failed = events.getLast();
        assertEquals(OperationalEventCode.RECOVERY_FAILED, failed.code());
        assertEquals("IllegalStateException", failed.attributes().get("errorType"));
        assertEquals(1L, metrics.snapshot().counters().get("snapshot.recovery.failure"));
    }

    @Test
    void theReportCopiesItsListsAndRefusesAMissingOne() {
        KnowledgeSnapshotId id = KnowledgeSnapshotId.generate();
        List<KnowledgeSnapshotId> recovered = new ArrayList<>(List.of(id));

        SnapshotRecoveryService.RecoveryReport report = new SnapshotRecoveryService.RecoveryReport(recovered, List.of());
        recovered.clear();

        assertEquals(List.of(id), report.recoveredCandidates(), "the report does not follow the list it was given");
        NullPointerException missing = assertThrows(NullPointerException.class,
                () -> new SnapshotRecoveryService.RecoveryReport(List.of(), null));
        assertEquals("racedCandidates", missing.getMessage());
    }

    @Test
    void theRuntimeRecoversEveryProjectInIdentityOrderWithTheSafeDefaultCutoff() {
        ProjectSpecificationId other = ProjectSpecificationId.generate();
        store.projects.put(other, new ProjectStoreEntry(other, SourceLocator.file("other")));
        store.projects.put(projectId, new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        KnowledgeSnapshotMetadata elevenMinutesOld = snapshot(KnowledgeSnapshotState.BUILDING, NOW.minusSeconds(660));
        KnowledgeSnapshotMetadata fiveMinutesOld = snapshot(KnowledgeSnapshotState.BUILDING, NOW.minusSeconds(300));

        Map<ProjectSpecificationId, SnapshotRecoveryService.RecoveryReport> reports =
                new RuntimeSnapshotRecovery(store, recorder).recoverAll(NOW);

        List<ProjectSpecificationId> expectedOrder = new ArrayList<>(List.of(projectId, other));
        expectedOrder.sort(ProjectSpecificationId::compareTo);
        assertEquals(expectedOrder, List.copyOf(reports.keySet()));
        assertEquals(List.of(elevenMinutesOld.id()), reports.get(projectId).recoveredCandidates());
        assertTrue(reports.get(other).recoveredCandidates().isEmpty());
        assertEquals(KnowledgeSnapshotState.BUILDING, store.state(fiveMinutesOld.id()),
                "the default policy keeps a candidate younger than ten minutes");
    }

    @Test
    void theRuntimeAppliesTheCutoffOfTheGivenPolicy() {
        store.projects.put(projectId, new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        KnowledgeSnapshotMetadata twoMinutesOld = snapshot(KnowledgeSnapshotState.BUILDING, NOW.minusSeconds(120));
        RuntimeSnapshotRecovery runtime = new RuntimeSnapshotRecovery(
                store,
                new SnapshotRecoveryService(store, recorder),
                new SnapshotRecoveryPolicy(Duration.ofMinutes(1)));

        Map<ProjectSpecificationId, SnapshotRecoveryService.RecoveryReport> reports = runtime.recoverAll(NOW);

        assertEquals(List.of(twoMinutesOld.id()), reports.get(projectId).recoveredCandidates());
        NullPointerException missingNow = assertThrows(NullPointerException.class, () -> runtime.recoverAll(null));
        assertEquals("now", missingNow.getMessage());
    }

    @Test
    void thePolicyRefusesANonPositiveDelayAndPlacesTheCutoffThatFarBehind() {
        assertEquals(NOW.minus(Duration.ofMinutes(10)), SnapshotRecoveryPolicy.safeDefaults().cutoffAt(NOW));
        assertEquals(NOW.minusSeconds(30), new SnapshotRecoveryPolicy(Duration.ofSeconds(30)).cutoffAt(NOW));

        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> new SnapshotRecoveryPolicy(Duration.ZERO));
        assertEquals("staleAfter must be greater than zero", zero.getMessage());
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> new SnapshotRecoveryPolicy(Duration.ofSeconds(-1)));
        assertEquals("staleAfter must be greater than zero", negative.getMessage());
        NullPointerException missingNow = assertThrows(NullPointerException.class,
                () -> SnapshotRecoveryPolicy.safeDefaults().cutoffAt(null));
        assertEquals("now", missingNow.getMessage());
    }

    private KnowledgeSnapshotMetadata snapshot(KnowledgeSnapshotState state, Instant createdAt) {
        KnowledgeSnapshotMetadata snapshot = new KnowledgeSnapshotMetadata(
                KnowledgeSnapshotId.generate(), projectId, Optional.empty(), state, Optional.of("rev"), createdAt);
        store.snapshots.put(snapshot.id(), snapshot);
        return snapshot;
    }

    private static final class Store implements SpecificationKnowledgeStore {
        private final Map<ProjectSpecificationId, ProjectStoreEntry> projects = new LinkedHashMap<>();
        private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> snapshots = new LinkedHashMap<>();
        private final Set<KnowledgeSnapshotId> racing = new HashSet<>();
        private RuntimeException listingFailure;

        KnowledgeSnapshotState state(KnowledgeSnapshotId id) {
            return snapshots.get(id).state();
        }

        @Override public List<ProjectStoreEntry> listProjects() {
            return List.copyOf(projects.values());
        }

        @Override public List<KnowledgeSnapshotMetadata> listSnapshots(ProjectSpecificationId id) {
            if (listingFailure != null) {
                throw listingFailure;
            }
            return snapshots.values().stream().filter(snapshot -> snapshot.projectId().equals(id)).toList();
        }

        @Override public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId id, KnowledgeSnapshotState from, KnowledgeSnapshotState to) {
            if (racing.contains(id)) {
                throw new SnapshotConflictException("snapshot moved concurrently: " + id);
            }
            KnowledgeSnapshotMetadata current = snapshots.get(id);
            assertEquals(from, current.state(), "the recovery transitions from the state it read");
            KnowledgeSnapshotMetadata updated = current.withState(to);
            snapshots.put(id, updated);
            return updated;
        }

        @Override public void putProject(ProjectStoreEntry project) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId id) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { throw new UnsupportedOperationException(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata snapshot) { throw new UnsupportedOperationException(); }
        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId id) { throw new UnsupportedOperationException(); }
        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId id) { throw new UnsupportedOperationException(); }
        @Override public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId id, Optional<KnowledgeSnapshotId> expected) {
            throw new UnsupportedOperationException();
        }
    }
}
