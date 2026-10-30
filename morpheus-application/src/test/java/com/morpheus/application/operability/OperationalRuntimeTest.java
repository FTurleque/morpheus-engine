package com.morpheus.application.operability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A runtime is a value: its mode chooses the sink, and two runtimes share no counter. */
class OperationalRuntimeTest {
    private static final OperationalEvent EVENT = new OperationalEvent(
            Instant.parse("2026-10-30T08:00:00Z"), OperationalEventLevel.INFO, OperationalEventCode.DATABASE_READY,
            Map.of("projectCount", "1"));

    @Test
    void theOffModeWritesNothingAndRefusesALogFile() {
        OperationalRuntime runtime = OperationalRuntime.of(OperationalLogMode.OFF);

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> runtime.writeLogFileIn(Path.of("logs")));

        assertFalse(runtime.writesEvents());
        assertEquals("the operational log mode writes no file", refusal.getMessage());
    }

    @Test
    void theJsonLinesModeWritesEachEventToTheStreamItWasHanded() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OperationalRuntime runtime = OperationalRuntime.of(
                OperationalLogMode.JSON_LINES, new PrintStream(bytes, true, StandardCharsets.UTF_8));

        runtime.sink().emit(EVENT);
        runtime.recorder().begin("probe", OperationalEventCode.DATABASE_READY, Map.of());

        assertTrue(runtime.writesEvents());
        String written = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(2, written.lines().count(), written);
        assertTrue(written.contains("DATABASE_READY"), written);
        assertEquals(1L, runtime.metrics().snapshot().counters().get("probe.started"));
    }

    @Test
    void theFileModeWritesOnlyOnceItsDirectoryIsNamedAndOnlyOnce(@TempDir Path logs) throws Exception {
        OperationalRuntime runtime = OperationalRuntime.of(OperationalLogMode.FILE);
        runtime.sink().emit(EVENT);

        runtime.writeLogFileIn(logs);
        runtime.sink().emit(EVENT);
        IllegalStateException again = assertThrows(IllegalStateException.class, () -> runtime.writeLogFileIn(logs));

        assertEquals("the operational log already has a destination", again.getMessage());
        Path file = logs.resolve(RotatingOperationalLogFile.FILE_NAME);
        assertEquals(1, Files.readAllLines(file, StandardCharsets.UTF_8).size(),
                "an event emitted before the directory was named is not kept");
    }

    @Test
    void twoRuntimesShareNoCounter() {
        OperationalRuntime first = OperationalRuntime.of(OperationalLogMode.OFF);
        OperationalRuntime second = OperationalRuntime.of(OperationalLogMode.OFF);

        first.metrics().increment("only.first");

        assertNotSame(first.metrics(), second.metrics());
        assertEquals(Map.of(), second.metrics().snapshot().counters());
    }
}
