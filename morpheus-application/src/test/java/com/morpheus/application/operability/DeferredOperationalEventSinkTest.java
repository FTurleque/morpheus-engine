package com.morpheus.application.operability;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The destination of the operational log is named once per process. */
class DeferredOperationalEventSinkTest {
    private static final OperationalEvent EVENT = new OperationalEvent(
            Instant.parse("2026-10-13T10:00:00Z"), OperationalEventLevel.INFO,
            OperationalEventCode.THIRD_PARTY_LOG, Map.of());

    @Test
    void eventsReachTheDestinationFromTheMomentItIsNamed() {
        List<OperationalEvent> received = new ArrayList<>();
        DeferredOperationalEventSink sink = new DeferredOperationalEventSink();

        sink.emit(EVENT);
        sink.directTo(received::add);
        sink.emit(EVENT);

        assertEquals(List.of(EVENT), received);
    }

    @Test
    void aSecondDestinationIsRefusedAndTheFirstKeepsReceiving() {
        List<OperationalEvent> first = new ArrayList<>();
        List<OperationalEvent> second = new ArrayList<>();
        DeferredOperationalEventSink sink = new DeferredOperationalEventSink();
        sink.directTo(first::add);

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> sink.directTo(second::add));
        sink.emit(EVENT);

        assertEquals("the operational log already has a destination", refused.getMessage());
        assertEquals(List.of(EVENT), first);
        assertEquals(List.of(), second);
    }
}
