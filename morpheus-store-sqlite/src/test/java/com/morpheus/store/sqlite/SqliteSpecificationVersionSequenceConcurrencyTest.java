package com.morpheus.store.sqlite;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteSpecificationVersionSequenceConcurrencyTest {

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
                Future<Long> left = executor.submit(() -> reserve(first, projectId, ready, start));
                Future<Long> right = executor.submit(() -> reserve(second, projectId, ready, start));
                ready.await();
                start.countDown();

                assertEquals(Set.of(1L, 2L), Set.of(value(left), value(right)));
            } finally {
                executor.shutdownNow();
            }
        }

        try (var reopened = new SqliteVersionedRequirementStore(database)) {
            assertEquals(3L, reopened.nextSpecificationVersionSequence(projectId),
                    "reservations must survive store/process boundaries even when no version row was written");
        }
    }

    private long reserve(
            SqliteVersionedRequirementStore store,
            ProjectSpecificationId projectId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return store.nextSpecificationVersionSequence(projectId);
    }

    private long value(Future<Long> future) throws Exception {
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
