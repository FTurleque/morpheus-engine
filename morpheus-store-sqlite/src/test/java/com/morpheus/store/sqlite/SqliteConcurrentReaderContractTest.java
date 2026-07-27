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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteConcurrentReaderContractTest {

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
        AtomicBoolean keepReading = new AtomicBoolean(true);
        CountDownLatch readersStarted = new CountDownLatch(3);

        try (var executor = Executors.newFixedThreadPool(4);
             SqliteSpecificationKnowledgeStore readerA = new SqliteSpecificationKnowledgeStore(database, 2_000);
             SqliteSpecificationKnowledgeStore readerB = new SqliteSpecificationKnowledgeStore(database, 2_000);
             SqliteSpecificationKnowledgeStore readerC = new SqliteSpecificationKnowledgeStore(database, 2_000);
             SqliteSpecificationKnowledgeStore writer = new SqliteSpecificationKnowledgeStore(database, 2_000)) {

            for (SqliteSpecificationKnowledgeStore reader : List.of(readerA, readerB, readerC)) {
                executor.submit(() -> {
                    readersStarted.countDown();
                    try {
                        while (keepReading.get()) {
                            KnowledgeSnapshotMetadata active = reader.activeSnapshot(projectId)
                                    .orElseThrow(() -> new AssertionError("reader observed no ACTIVE snapshot"));
                            observations.add(active.id());
                            if (active.state() != KnowledgeSnapshotState.ACTIVE) {
                                throw new AssertionError("reader observed non-ACTIVE metadata from activeSnapshot");
                            }
                        }
                    } catch (Throwable failure) {
                        failures.add(failure);
                    }
                });
            }

            assertTrue(readersStarted.await(5, TimeUnit.SECONDS), "all readers must start before activation");
            Thread.sleep(50L);
            writer.activateSnapshot(nextId, Optional.of(previousActiveId));
            Thread.sleep(50L);
            keepReading.set(false);
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "reader/writer tasks must terminate");
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
