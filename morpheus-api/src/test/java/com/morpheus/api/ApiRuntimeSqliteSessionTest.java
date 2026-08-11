package com.morpheus.api;

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

class ApiRuntimeSqliteSessionTest {
    @TempDir
    Path temp;

    @Test
    void nineApiStoresBorrowOnePhysicalConnectionAndRunSchemaOnce() {
        try (ApiRuntime runtime = new ApiRuntime(temp.resolve("single.db"))) {
            assertEquals(9, runtime.logicalSqliteConnectionsBorrowed());
            assertEquals(1, runtime.sqliteSchemaInitializations());
        }
    }

    @Test
    void concurrentLocalApiOperationsOpenOnePhysicalConnectionEachAndLeakNone() throws Exception {
        Path database = temp.resolve("concurrent.db");
        MorpheusApiService api = new MorpheusApiService(database);
        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();
        int operations = 24;
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = new ArrayList<Callable<Object>>();
            for (int index = 0; index < operations; index++) calls.add(api::listProjects);
            for (var result : executor.invokeAll(calls)) assertEquals(List.of(), result.get());
        }
        SqliteConnectionScope.Diagnostics after = SqliteConnectionScope.diagnostics();
        assertEquals(operations, after.opened() - before.opened());
        assertEquals(operations, after.closed() - before.closed());
        assertEquals(before.active(), after.active());
        assertTrue(after.peak() <= Math.max(before.peak(), before.active() + 8));
    }
}
