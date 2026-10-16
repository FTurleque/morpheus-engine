package com.morpheus.api;

import com.morpheus.application.operability.LocalOperabilityService;
import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.operability.SuppressedFailureFamily;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every family of suppressed failure is served by the metrics projection, under the name the family gives it.
 *
 * <p>The metrics route answers with this projection as it is. It holds no list of counter names: a family added to
 * {@link SuppressedFailureFamily} is served the first time it is counted, and one that never moved is absent. The
 * names are therefore read here, not in the OpenAPI document, which describes the response as a generic envelope.</p>
 */
class SuppressedFailureCountersServedTest {
    private final OperationalMetrics metrics = new OperationalMetrics();
    private final MorpheusOperabilityApiService service = new MorpheusOperabilityApiService(
            new LocalOperabilityService(() -> 0, metrics, OperationalEventSink.noop()));

    @Test
    void aFamilyThatNeverMovedIsAbsentAndEveryFamilyThatMovedIsServedUnderItsOwnName() {
        assertEquals(Map.of(), service.metrics().counters());

        OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);
        Map<String, Long> expected = new TreeMap<>();
        for (SuppressedFailureFamily family : SuppressedFailureFamily.values()) {
            recorder.suppressed(family, new IllegalStateException("set aside"));
            expected.put("failure.suppressed." + family.key(), 1L);
        }

        assertTrue(expected.size() >= 12, () -> "families went missing: " + expected.keySet());
        assertEquals(expected, new TreeMap<>(service.metrics().counters()));
    }

    @Test
    void whatTheRecorderOfTheServiceCountsIsWhatItsMetricsServe() {
        service.recorder().suppressed(SuppressedFailureFamily.PLUGIN_PROBE_SHUTDOWN, new IllegalStateException("x"));
        service.recorder().suppressed(SuppressedFailureFamily.PLUGIN_PROBE_SHUTDOWN, new IllegalStateException("y"));

        assertEquals(Map.of("failure.suppressed.plugin-probe-shutdown", 2L), service.metrics().counters());
    }
}
