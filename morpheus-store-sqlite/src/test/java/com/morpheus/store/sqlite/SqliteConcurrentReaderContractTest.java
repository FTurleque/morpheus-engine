package com.morpheus.store.sqlite;

import com.morpheus.application.snapshot.SnapshotLifecycleService;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteConcurrentReaderContractTest {
    private static final int READERS = 3;

    @TempDir
    Path tempDir;

    @Test
    void readersSeeEitherPreviousOrNewActiveButNeverAnEmptyOrPartialPublishedState() throws Exception {
        Path database = tempDir.resolve("concurrent-readers.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId previousActiveId;
        KnowledgeSnapshotId nextId;

        try (SqliteSpecificationKnowledgeStore setup = new SqliteSpecificationKnowledgeStore(database)) {
            setup.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("m19/concurrent-readers")));
            SnapshotLifecycleService lifecycle = new SnapshotLifecycleService(setup);
            KnowledgeSnapshotMetadata previous = readyCandidate(setup, projectId, Optional.empty(), "previous", 0);
            previousActiveId = lifecycle.activate(previous.id()).id();
            KnowledgeSnapshotMetadata next = readyCandidate(
                    setup,
                    projectId,
                    Optional.of(previousActiveId),
                    "next",
                    1);
            nextId = next.id();
        }

        ConcurrentLinkedQueue<KnowledgeSnapshotId> observations = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        CountDownLatch observedPrevious = new CountDownLatch(READERS);
        CountDownLatch activationStarting = new CountDownLatch(1);
        CountDownLatch observedNext = new CountDownLatch(READERS);

        // The production busy timeout, not a shorter one: what this test pins is that a reader never observes a
        // half-published state, and SqliteConcurrencyHardeningTest owns the busy/lock contract with its own
        // deliberately short timeouts. Squeezing the timeout here only made the reader/writer exclusion of the
        // PERSIST rollback journal surface as a spurious SQLITE_BUSY under disk load.
        try (var executor = Executors.newFixedThreadPool(READERS);
             SqliteSpecificationKnowledgeStore readerA = new SqliteSpecificationKnowledgeStore(database);
             SqliteSpecificationKnowledgeStore readerB = new SqliteSpecificationKnowledgeStore(database);
             SqliteSpecificationKnowledgeStore readerC = new SqliteSpecificationKnowledgeStore(database);
             SqliteSpecificationKnowledgeStore writer = new SqliteSpecificationKnowledgeStore(database)) {

            for (SqliteSpecificationKnowledgeStore reader : List.of(readerA, readerB, readerC)) {
                executor.submit(() -> {
                    try {
                        // Reaching the activation is not the same as having read through it. Each reader proves
                        // it observed the published predecessor before the writer is allowed to replace it, so
                        // "at least one reader saw the previous ACTIVE" is established rather than assumed.
                        assertObservedActive(reader, projectId, observations);
                        observedPrevious.countDown();

                        activationStarting.await();
                        // Each reader reads until it has itself seen the replacement, so all three are proven to
                        // have read across the transition rather than one of them ending the round for everyone.
                        KnowledgeSnapshotId seen = previousActiveId;
                        while (!seen.equals(nextId)) {
                            seen = assertObservedActive(reader, projectId, observations);
                            // Not synchronization: every ordering guarantee here comes from the latches. This
                            // only keeps three readers from holding the shared lock back to back, which starves
                            // the writer under a rollback journal and has nothing to do with what is asserted.
                            TimeUnit.MILLISECONDS.sleep(1L);
                        }
                        observedNext.countDown();
                    } catch (Throwable failure) {
                        failures.add(failure);
                        // Never leave the main thread waiting on a reader that has already given up.
                        observedPrevious.countDown();
                        observedNext.countDown();
                    }
                });
            }

            assertTrue(observedPrevious.await(30, TimeUnit.SECONDS),
                    () -> "every reader must observe the published previous ACTIVE before activation: " + failures);
            assertTrue(observations.contains(previousActiveId),
                    "the readers must have observed the previous ACTIVE before the writer replaces it");

            // The readers resume exactly as the activation starts, so the replacement happens while they read.
            activationStarting.countDown();
            writer.activateSnapshot(nextId, Optional.of(previousActiveId));

            assertTrue(observedNext.await(30, TimeUnit.SECONDS),
                    () -> "every reader must read through the activation and observe the new ACTIVE: " + failures);
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "reader tasks must terminate");
        }

        assertTrue(failures.isEmpty(), () -> "concurrent reader failures: " + failures);
        assertFalse(observations.isEmpty(), "readers must have observed published state during activation");
        assertTrue(observations.stream().allMatch(id -> id.equals(previousActiveId) || id.equals(nextId)),
                () -> "reader observed an impossible snapshot id: " + observations);
        assertTrue(observations.contains(previousActiveId), "at least one reader must observe the previous ACTIVE");
        assertTrue(observations.contains(nextId), "at least one reader must observe the new ACTIVE");

        try (SqliteSpecificationKnowledgeStore verify = new SqliteSpecificationKnowledgeStore(database)) {
            assertEquals(nextId, verify.activeSnapshot(projectId).orElseThrow().id());
            assertEquals(1L, verify.listSnapshots(projectId).stream()
                    .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                    .count());
        }
    }

    /** One published read: absent or non-ACTIVE metadata is exactly the partial state this test forbids. */
    private static KnowledgeSnapshotId assertObservedActive(
            SqliteSpecificationKnowledgeStore reader,
            ProjectSpecificationId projectId,
            ConcurrentLinkedQueue<KnowledgeSnapshotId> observations) {
        KnowledgeSnapshotMetadata active = reader.activeSnapshot(projectId)
                .orElseThrow(() -> new AssertionError("reader observed no ACTIVE snapshot"));
        if (active.state() != KnowledgeSnapshotState.ACTIVE) {
            throw new AssertionError("reader observed non-ACTIVE metadata from activeSnapshot");
        }
        observations.add(active.id());
        return active.id();
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
                Instant.parse("2026-07-26T12:30:00Z").plusSeconds(offsetSeconds));
        store.putSnapshot(candidate);
        store.transitionSnapshotState(candidate.id(), KnowledgeSnapshotState.BUILDING, KnowledgeSnapshotState.VALIDATING);
        return store.transitionSnapshotState(candidate.id(), KnowledgeSnapshotState.VALIDATING, KnowledgeSnapshotState.READY);
    }
}
