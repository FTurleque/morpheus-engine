package com.morpheus.api;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.sqlite.SqliteConnectionScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The runtime of an HTTP operation counts its connection and its schema check on the recorder its service was handed.
 * Each test hands its own and reads absolute values: nothing else in the JVM writes there.
 */
class ApiRuntimeSqliteSessionTest {
    @TempDir
    Path temp;

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);

    @Test
    void nineApiStoresBorrowOnePhysicalConnectionAndRunSchemaOnce() {
        try (ApiRuntime runtime = new ApiRuntime(temp.resolve("single.db"), recorder)) {
            assertEquals(0, runtime.logicalSqliteConnectionsBorrowed(), "a store is built when an operation asks for it");
            List.of(runtime.snapshots(), runtime.requirements(), runtime.content(), runtime.traceability(),
                    runtime.externalReferences(), runtime.identities(), runtime.syncState(),
                    runtime.lifecycleMutations(), runtime.compositions());
            assertEquals(9, runtime.logicalSqliteConnectionsBorrowed());
            assertEquals(1, runtime.sqliteSchemaInitializations());
        }
        assertEquals(1L, counter(SqliteConnectionScope.SCHEMA_CHECKS));
    }

    @Test
    void queryRuntimeSharesOnePhysicalConnectionAcrossItsFiveStores() {
        Path database = temp.resolve("query.db");

        Object views = new MorpheusQueryApiService(database, recorder).listSavedViews(
                "PROJECT", ProjectSpecificationId.generate().toString());

        assertTrue(views instanceof List<?>);
        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_OPENED));
        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_CLOSED));
        assertEquals(1L, counter(SqliteConnectionScope.SCHEMA_CHECKS));
    }

    /**
     * Twenty-four operations on eight threads: one connection and one schema check each, all released. The peak of
     * connections held at once was a gauge of the process; counters on a recorder cannot express it, and the eight
     * threads of the pool bound it.
     */
    @Test
    void concurrentLocalApiOperationsOpenOnePhysicalConnectionEachAndLeakNone() throws Exception {
        Path database = temp.resolve("concurrent.db");
        MorpheusApiService api = new MorpheusApiService(database, recorder);
        int operations = 24;
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = new ArrayList<Callable<Object>>();
            for (int index = 0; index < operations; index++) calls.add(api::listProjects);
            for (var result : executor.invokeAll(calls)) assertEquals(List.of(), result.get());
        }
        assertEquals(operations, counter(SqliteConnectionScope.CONNECTIONS_OPENED));
        assertEquals(operations, counter(SqliteConnectionScope.CONNECTIONS_CLOSED));
        assertEquals(operations, counter(SqliteConnectionScope.SCHEMA_CHECKS));
    }

    private long counter(String name) {
        return metrics.snapshot().counters().getOrDefault(name, 0L);
    }
}
