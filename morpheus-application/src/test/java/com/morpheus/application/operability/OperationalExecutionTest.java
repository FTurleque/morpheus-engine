package com.morpheus.application.operability;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalExecutionTest {

    @Test
    void providerAndExternalCallsRecordLocalDurationsAndStableOutcomesWithoutPayloads() {
        OperationalMetrics metrics = new OperationalMetrics();
        List<OperationalEvent> events = new ArrayList<>();
        OperationalRecorder recorder = new OperationalRecorder(events::add, metrics);
        OperationalExecution execution = new OperationalExecution(recorder);

        assertEquals("provider-result", execution.providerRead("structured-markdown", () -> "provider-result"));
        assertEquals("external-result", execution.externalCall("MINOS", "resolve-symbol", () -> "external-result"));
        assertThrows(IllegalStateException.class,
                () -> execution.externalCall("NEXUS", "build-context", () -> {
                    throw new IllegalStateException("simulated unavailable dependency");
                }));

        OperationalMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(1L, snapshot.counters().get("provider.read.success"));
        assertEquals(1L, snapshot.counters().get("external.minos.success"));
        assertEquals(1L, snapshot.counters().get("external.nexus.failure"));
        assertEquals(1L, snapshot.timings().get("provider.read.duration").count());
        assertEquals(1L, snapshot.timings().get("external.minos.duration").count());
        assertEquals(1L, snapshot.timings().get("external.nexus.duration").count());
        assertTrue(snapshot.timings().values().stream().allMatch(timing -> timing.totalNanos() >= 0L));

        assertEquals(List.of(
                        OperationalEventCode.PROVIDER_READ_STARTED,
                        OperationalEventCode.PROVIDER_READ_COMPLETED,
                        OperationalEventCode.EXTERNAL_CALL_STARTED,
                        OperationalEventCode.EXTERNAL_CALL_COMPLETED,
                        OperationalEventCode.EXTERNAL_CALL_STARTED,
                        OperationalEventCode.EXTERNAL_CALL_FAILED),
                events.stream().map(OperationalEvent::code).toList());
        assertTrue(events.stream().allMatch(event -> event.occurredAt().isBefore(Instant.now().plusSeconds(1))));
        assertTrue(events.stream().noneMatch(event -> event.attributes().values().stream()
                .anyMatch(value -> value.contains("provider-result") || value.contains("external-result"))));
    }
}
