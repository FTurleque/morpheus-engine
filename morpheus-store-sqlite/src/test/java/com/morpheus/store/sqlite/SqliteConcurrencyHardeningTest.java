package com.morpheus.store.sqlite;

import com.morpheus.application.snapshot.SnapshotLifecycleService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteConcurrencyHardeningTest {

    @TempDir
    Path tempDir;

    @Test
    void lockedDatabaseFailsWithinConfiguredBoundAndRecoversAfterUnlock() throws Exception {
        Path database = tempDir.resolve("locked.db");
        try (SqliteSpecificationKnowledgeStore bootstrap = new SqliteSpecificationKnowledgeStore(database)) {
            // Initializes schema before the explicit lock is acquired.
        }

        try (SqliteSpecificationKnowledgeStore contender = new SqliteSpecificationKnowledgeStore(database, 150);
             Connection locker = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath().normalize())) {
            try (Statement statement = locker.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 150");
                statement.execute("BEGIN EXCLUSIVE");
            }

            ProjectStoreEntry project = new ProjectStoreEntry(
                    ProjectSpecificationId.generate(),
                    SourceLocator.file("m19/locked-project"));
            long started = System.nanoTime();
            KnowledgeStoreException failure = assertThrows(KnowledgeStoreException.class, () -> contender.putProject(project));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertTrue(elapsed.compareTo(Duration.ofSeconds(2)) < 0,
                    () -> "SQLite lock failure exceeded bounded test window: " + elapsed);
            assertNotNull(failure.getCause(), "locked SQLite write must preserve the JDBC cause");

            try (Statement statement = locker.createStatement()) {
                statement.execute("ROLLBACK");
            }

            contender.putProject(project);
            assertEquals(project, contender.findProject(project.id()).orElseThrow());
        }
    }

    @Test
    void concurrentSuccessorActivationsProduceExactlyOneNewActiveSnapshot() throws Exception {
        Path database = tempDir.resolve("concurrent-activation.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId firstCandidateId;
        KnowledgeSnapshotId secondCandidateId;

        try (SqliteSpecificationKnowledgeStore setup = new SqliteSpecificationKnowledgeStore(database)) {
            setup.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("m19/concurrent-project")));
            SnapshotLifecycleService lifecycle = new SnapshotLifecycleService(setup);
            KnowledgeSnapshotMetadata initial = readyCandidate(setup, projectId, Optional.empty(), "initial", 0);
            KnowledgeSnapshotMetadata active = lifecycle.activate(initial.id());
            KnowledgeSnapshotMetadata first = readyCandidate(setup, projectId, Optional.of(active.id()), "candidate-a", 1);
            KnowledgeSnapshotMetadata second = readyCandidate(setup, projectId, Optional.of(active.id()), "candidate-b", 2);
            firstCandidateId = first.id();
            secondCandidateId = second.id();
        }

        List<Object> outcomes = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2);
             SqliteSpecificationKnowledgeStore firstStore = new SqliteSpecificationKnowledgeStore(database, 1_000);
             SqliteSpecificationKnowledgeStore secondStore = new SqliteSpecificationKnowledgeStore(database, 1_000)) {
            executor.submit(() -> activateAfterBarrier(firstStore, firstCandidateId, ready, start, outcomes));
            executor.submit(() -> activateAfterBarrier(secondStore, secondCandidateId, ready, start, outcomes));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "both activation commands must reach the start barrier");
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "activation commands must terminate");
        }

        long successes = outcomes.stream().filter(KnowledgeSnapshotMetadata.class::isInstance).count();
        long explicitFailures = outcomes.stream().filter(KnowledgeStoreException.class::isInstance).count();
        assertEquals(1L, successes, () -> "exactly one successor must activate, outcomes=" + outcomes);
        assertEquals(1L, explicitFailures, () -> "losing command must fail explicitly, outcomes=" + outcomes);

        try (SqliteSpecificationKnowledgeStore verify = new SqliteSpecificationKnowledgeStore(database)) {
            KnowledgeSnapshotMetadata active = verify.activeSnapshot(projectId).orElseThrow();
            assertTrue(active.id().equals(firstCandidateId) || active.id().equals(secondCandidateId));
            assertEquals(1L, verify.listSnapshots(projectId).stream()
                    .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                    .count());
            assertEquals(2L, verify.listSnapshots(projectId).stream()
                    .filter(snapshot -> snapshot.id().equals(firstCandidateId) || snapshot.id().equals(secondCandidateId))
                    .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.ACTIVE
                            || snapshot.state() == KnowledgeSnapshotState.READY)
                    .count());
        }
    }

    private KnowledgeSnapshotMetadata readyCandidate(
            SqliteSpecificationKnowledgeStore store,
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessor,
            String revision,
            long offsetSeconds) {
        KnowledgeSnapshotMetadata candidate = new KnowledgeSnapshotMetadata(
                KnowledgeSnapshotId.generate(),
                projectId,
                predecessor,
                KnowledgeSnapshotState.BUILDING,
                Optional.of(revision),
                Instant.parse("2026-07-26T12:00:00Z").plusSeconds(offsetSeconds));
        store.putSnapshot(candidate);
        store.transitionSnapshotState(candidate.id(), KnowledgeSnapshotState.BUILDING, KnowledgeSnapshotState.VALIDATING);
        return store.transitionSnapshotState(candidate.id(), KnowledgeSnapshotState.VALIDATING, KnowledgeSnapshotState.READY);
    }

    private void activateAfterBarrier(
            SqliteSpecificationKnowledgeStore store,
            KnowledgeSnapshotId candidateId,
            CountDownLatch ready,
            CountDownLatch start,
            List<Object> outcomes) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                outcomes.add(new IllegalStateException("activation start barrier timed out"));
                return;
            }
            outcomes.add(store.activateSnapshot(candidateId, store.findSnapshot(candidateId).orElseThrow().predecessorId()));
        } catch (KnowledgeStoreException expected) {
            outcomes.add(expected);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            outcomes.add(interrupted);
        } catch (RuntimeException unexpected) {
            outcomes.add(unexpected);
        }
    }
}
