package com.morpheus.store.sqlite;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteSpecificationVersionSequenceConcurrencyTest {
    private static final int RESERVATIONS_PER_STORE = 8;

    @TempDir
    Path tempDir;

    @Test
    void independentStoresReserveDistinctDurableSequencesWithoutPersistingVersions() throws Exception {
        Path database = tempDir.resolve("concurrent-sequences.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        try (var projects = new SqliteSpecificationKnowledgeStore(database)) {
            projects.putProject(new ProjectStoreEntry(projectId, new SourceLocator("file", "workspace")));
        }

        try (var first = new SqliteVersionedRequirementStore(database);
             var second = new SqliteVersionedRequirementStore(database)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<List<Long>> left = executor.submit(() -> reserveMany(first, projectId, ready, start));
                Future<List<Long>> right = executor.submit(() -> reserveMany(second, projectId, ready, start));
                ready.await();
                start.countDown();

                List<Long> all = new ArrayList<>();
                all.addAll(value(left));
                all.addAll(value(right));
                assertEquals(expectedSequences(RESERVATIONS_PER_STORE * 2), new HashSet<>(all));
                assertEquals(RESERVATIONS_PER_STORE * 2, all.size(), "every reservation must be unique");
            } finally {
                executor.shutdownNow();
            }
        }

        try (var reopened = new SqliteVersionedRequirementStore(database)) {
            assertEquals(17L, reopened.nextSpecificationVersionSequence(projectId),
                    "reservations must survive store/process boundaries even when no version row was written");
        }
    }

    private List<Long> reserveMany(
            SqliteVersionedRequirementStore store,
            ProjectSpecificationId projectId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        List<Long> reserved = new ArrayList<>();
        for (int index = 0; index < RESERVATIONS_PER_STORE; index++) {
            reserved.add(store.nextSpecificationVersionSequence(projectId));
        }
        return List.copyOf(reserved);
    }

    private Set<Long> expectedSequences(int count) {
        Set<Long> expected = new HashSet<>();
        for (long sequence = 1; sequence <= count; sequence++) expected.add(sequence);
        return expected;
    }

    private <T> T value(Future<T> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw failure;
        }
    }
}
