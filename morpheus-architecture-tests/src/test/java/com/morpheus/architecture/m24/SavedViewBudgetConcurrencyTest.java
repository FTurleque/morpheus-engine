package com.morpheus.architecture.m24;

import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.application.query.saved.SavedViewVersion;
import com.morpheus.application.store.SavedViewStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.memory.MemorySavedViewStore;
import com.morpheus.store.sqlite.SqliteSavedViewStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedViewBudgetConcurrencyTest {
    private static final Instant NOW = Instant.parse("2026-09-06T12:30:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memorySavedViewBudgetIsAtomic() throws Exception {
        MemorySavedViewStore store = new MemorySavedViewStore();
        assertBudgetAtomic(store, store);
    }

    @Test
    void sqliteSavedViewBudgetIsAtomicAcrossIndependentStores() throws Exception {
        Path database = tempDir.resolve("saved-view-budget.db");
        try (SqliteSavedViewStore first = new SqliteSavedViewStore(database);
             SqliteSavedViewStore second = new SqliteSavedViewStore(database)) {
            assertBudgetAtomic(first, second);
        }
    }

    private void assertBudgetAtomic(SavedViewStore first, SavedViewStore second) throws Exception {
        ProjectQueryScope scope = new ProjectQueryScope(ProjectSpecificationId.generate());
        QueryDefinition query = QueryDefinition.all(scope, QueryEntityType.CHANGE, QueryPage.first(10));
        for (int index = 0; index < QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE - 1; index++) {
            SavedViewDefinition definition = definition(index, query);
            first.create(definition, version(definition));
        }

        SavedViewDefinition left = definition(QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE, query);
        SavedViewDefinition right = definition(QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE + 1, query);
        int successes = race(
                () -> first.create(left, version(left)),
                () -> second.create(right, version(right)));

        assertEquals(1, successes);
        assertEquals(QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE, first.count(scope));
    }

    private SavedViewDefinition definition(int index, QueryDefinition query) {
        return new SavedViewDefinition(
                SavedViewId.generate(),
                "atomic-view-" + index,
                query,
                1L,
                SavedViewStatus.ACTIVE,
                NOW,
                NOW);
    }

    private SavedViewVersion version(SavedViewDefinition definition) {
        return new SavedViewVersion(
                definition.id(),
                definition.revision(),
                definition.name(),
                definition.query(),
                definition.status(),
                definition.updatedAt());
    }

    private int race(Runnable left, Runnable right) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> leftResult = executor.submit(() -> participate(ready, start, left));
            Future<Boolean> rightResult = executor.submit(() -> participate(ready, start, right));
            ready.await();
            start.countDown();
            return (leftResult.get() ? 1 : 0) + (rightResult.get() ? 1 : 0);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean participate(CountDownLatch ready, CountDownLatch start, Runnable action) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            action.run();
            return true;
        } catch (IllegalStateException budgetFailure) {
            assertTrue(budgetFailure.getMessage().contains("budget"), budgetFailure::getMessage);
            return false;
        }
    }
}
