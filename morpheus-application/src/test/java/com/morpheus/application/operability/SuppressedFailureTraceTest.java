package com.morpheus.application.operability;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A failure the product deliberately does not propagate leaves a counter, and an event when a sink listens.
 *
 * <p>Such a failure used to leave nothing: the catch held a comment, and after the process ended nothing said that an
 * adapter had been present and broken, or that a request had died of something nobody had foreseen.</p>
 */
class SuppressedFailureTraceTest {
    @Test
    void theCounterOfTheFamilyMovesWhenNoSinkListens() {
        OperationalMetrics metrics = new OperationalMetrics();
        OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);

        recorder.suppressed(SuppressedFailureFamily.ADAPTER_DISCOVERY, new IllegalStateException("broken"));
        recorder.suppressed(SuppressedFailureFamily.ADAPTER_DISCOVERY, new IllegalStateException("broken again"));
        recorder.suppressed(SuppressedFailureFamily.HTTP_INTERNAL_ERROR, new NullPointerException());

        assertEquals(Map.of("failure.suppressed.adapter-discovery", 2L, "failure.suppressed.http-internal-error", 1L),
                metrics.snapshot().counters());
    }

    @Test
    void theEventNamesTheFamilyAndTheTypeAndNeverTheMessage() {
        List<OperationalEvent> events = new ArrayList<>();
        OperationalRecorder recorder = new OperationalRecorder(events::add, new OperationalMetrics());

        recorder.suppressed(SuppressedFailureFamily.HTTP_INTERNAL_ERROR,
                new IllegalStateException("cannot read /srv/morpheus/data/morpheus.db"));

        assertEquals(1, events.size());
        OperationalEvent event = events.get(0);
        assertEquals(OperationalEventCode.FAILURE_SUPPRESSED, event.code());
        assertEquals(OperationalEventLevel.ERROR, event.level());
        assertEquals(Map.of("family", "http-internal-error", "failureType", "IllegalStateException"),
                event.attributes());
    }

    @Test
    void theStructuredSinkWritesTheEventWithoutTheLocationTheFailureNamed() {
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        OperationalRecorder recorder = new OperationalRecorder(
                new StructuredOperationalEventSink(new PrintStream(written, true, StandardCharsets.UTF_8)),
                new OperationalMetrics());

        recorder.suppressing(SuppressedFailureFamily.ADAPTER_DISCOVERY)
                .accept(new IllegalStateException("adapter jar at C:\\plugins\\broken.jar is unreadable"));

        String line = written.toString(StandardCharsets.UTF_8);
        assertTrue(line.contains("\"code\":\"FAILURE_SUPPRESSED\""), line);
        assertTrue(line.contains("\"level\":\"WARNING\""), line);
        assertTrue(line.contains("\"family\":\"adapter-discovery\""), line);
        assertFalse(line.contains("plugins"), line);
    }

    @Test
    void everyFamilyHasACounterNameOfItsOwn() {
        List<String> counters = new ArrayList<>();
        for (SuppressedFailureFamily family : SuppressedFailureFamily.values()) {
            assertTrue(family.counter().equals("failure.suppressed." + family.key()), family::name);
            assertTrue(family.key().matches("[a-z]+(-[a-z]+)*"), family::key);
            assertFalse(counters.contains(family.counter()), family::counter);
            counters.add(family.counter());
        }
    }
}
