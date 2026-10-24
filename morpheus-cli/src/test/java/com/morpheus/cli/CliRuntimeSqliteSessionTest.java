package com.morpheus.cli;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.store.sqlite.runtime.SqliteConnectionScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A CLI invocation counts its connection on the recorder it was handed; the test hands its own. */
class CliRuntimeSqliteSessionTest {
    @TempDir
    Path temp;

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);

    @Test
    void oneCliInvocationUsesOnePhysicalSqliteConnectionAndLeaksNone() {
        try (CliRuntime ignored = CliRuntime.recovering(temp.resolve("cli.db"), recorder)) {
            assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_OPENED));
            assertEquals(0L, counter(SqliteConnectionScope.CONNECTIONS_CLOSED));
        }

        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_OPENED));
        assertEquals(1L, counter(SqliteConnectionScope.CONNECTIONS_CLOSED));
        assertEquals(1L, counter(SqliteConnectionScope.SCHEMA_CHECKS), "the recovery and the stores check it once");
    }

    private long counter(String name) {
        return metrics.snapshot().counters().getOrDefault(name, 0L);
    }
}
