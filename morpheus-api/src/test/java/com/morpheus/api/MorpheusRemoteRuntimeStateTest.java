package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MorpheusRemoteRuntimeStateTest {
    private static final Instant STARTED_AT = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void preservesRemoteStatusContractAndRecordsRuntimeOutcomes() {
        MorpheusRemoteRuntimeState runtime = state(STARTED_AT);

        runtime.recordRequest();
        runtime.recordRequest();
        runtime.requestStarted();
        runtime.recordAuthenticationFailure();
        runtime.recordAuthorizationFailure();
        runtime.recordThrottledRequest();
        runtime.recordRequestTimeout();

        Map<String, Object> status = runtime.statusAt("127.0.0.1", 9443, STARTED_AT.plusSeconds(5));

        assertEquals("REMOTE", status.get("mode"));
        assertEquals("HTTPS", status.get("transport"));
        assertEquals("127.0.0.1", status.get("host"));
        assertEquals(9443, status.get("port"));
        assertEquals(STARTED_AT.toString(), status.get("startedAt"));
        assertEquals(5L, status.get("uptimeSeconds"));
        assertEquals(1, status.get("activeRequests"));
        assertEquals(4, status.get("maxConcurrentRequests"));
        assertEquals(2, status.get("maxConcurrentPrivilegedRequests"));
        assertEquals(15_000L, status.get("requestBodyReadTimeoutMillis"));
        assertEquals(16 * 1024 * 1024, status.get("maxProxyResponseBytes"));
        assertEquals(128 * 1024 * 1024, status.get("maxProxyInFlightBytes"));
        assertEquals(8, status.get("maxConcurrentBufferedProxyResponses"));
        assertEquals(2L, status.get("totalRequests"));
        assertEquals(1L, status.get("authenticationFailures"));
        assertEquals(1L, status.get("authorizationFailures"));
        assertEquals(1L, status.get("throttledRequests"));
        assertEquals(1L, status.get("requestTimeouts"));

        runtime.requestFinished();
        assertEquals(0, runtime.statusAt("127.0.0.1", 9443, STARTED_AT.plusSeconds(6)).get("activeRequests"));
    }

    @Test
    void restoresCounterAndFailsClosedOnActiveRequestUnderflow() {
        MorpheusRemoteRuntimeState runtime = state(STARTED_AT);

        IllegalStateException failure = assertThrows(IllegalStateException.class, runtime::requestFinished);

        assertEquals("remote active request counter underflow", failure.getMessage());
        assertEquals(0, runtime.statusAt("localhost", 443, STARTED_AT).get("activeRequests"));
    }

    @Test
    void clampsNegativeUptimeAndExercisesLiveStatusSnapshot() {
        MorpheusRemoteRuntimeState deterministic = state(STARTED_AT);
        assertEquals(0L, deterministic.statusAt("localhost", 443, STARTED_AT.minusSeconds(30)).get("uptimeSeconds"));

        MorpheusRemoteRuntimeState live = new MorpheusRemoteRuntimeState(
                1,
                1,
                Duration.ofMillis(1),
                1,
                1,
                1);
        assertEquals("REMOTE", live.status("localhost", 443).get("mode"));
    }

    @Test
    void rejectsInvalidRuntimeBounds() {
        assertThrows(IllegalArgumentException.class, () -> new MorpheusRemoteRuntimeState(
                0, 1, Duration.ofSeconds(1), 1, 1, 1, STARTED_AT));
        assertThrows(IllegalArgumentException.class, () -> new MorpheusRemoteRuntimeState(
                1, 0, Duration.ofSeconds(1), 1, 1, 1, STARTED_AT));
        assertThrows(IllegalArgumentException.class, () -> new MorpheusRemoteRuntimeState(
                4, 5, Duration.ofSeconds(1), 1, 1, 1, STARTED_AT));
        Duration positiveDuration = Duration.ofSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> new MorpheusRemoteRuntimeState(
                1, 1, Duration.ofSeconds(1), 0, 1, 1, STARTED_AT));
        assertThrows(IllegalArgumentException.class, () -> new MorpheusRemoteRuntimeState(
                1, 1, positiveDuration, 1, 0, 1, STARTED_AT));
        assertThrows(IllegalArgumentException.class, () -> new MorpheusRemoteRuntimeState(
                1, 1, positiveDuration, 1, 1, 0, STARTED_AT));
        assertThrows(IllegalArgumentException.class, () -> new MorpheusRemoteRuntimeState(
                1, 1, Duration.ZERO, 1, 1, 1, STARTED_AT));
        Duration negativeDuration = Duration.ofSeconds(-1);
        assertThrows(IllegalArgumentException.class, () -> new MorpheusRemoteRuntimeState(
                1, 1, negativeDuration, 1, 1, 1, STARTED_AT));
        assertThrows(NullPointerException.class, () -> new MorpheusRemoteRuntimeState(
                1, 1, null, 1, 1, 1, STARTED_AT));
        assertThrows(NullPointerException.class, () -> new MorpheusRemoteRuntimeState(
                1, 1, Duration.ofSeconds(1), 1, 1, 1, null));
    }

    /**
     * A blocked mutation is invisible in an aggregate request count, which also moves with read traffic. The
     * privileged gauge and the age of the oldest privileged operation are what an operator reads instead.
     */
    @Test
    void reportsActivePrivilegedOperationsAndTheAgeOfTheOldestOne() {
        MorpheusRemoteRuntimeState runtime = state(STARTED_AT);

        long first = runtime.privilegedRequestStarted(0L);
        long second = runtime.privilegedRequestStarted(SECONDS.toNanos(30));

        Map<String, Object> saturated = runtime.statusAt(
                "127.0.0.1", 9443, STARTED_AT, SECONDS.toNanos(90));
        assertEquals(2, saturated.get("activePrivilegedRequests"));
        assertEquals(2L, saturated.get("totalPrivilegedRequests"));
        assertEquals(SECONDS.toMillis(90), saturated.get("oldestActivePrivilegedRequestMillis"));

        runtime.privilegedRequestFinished(first);
        Map<String, Object> afterOldestFinished = runtime.statusAt(
                "127.0.0.1", 9443, STARTED_AT, SECONDS.toNanos(90));
        assertEquals(1, afterOldestFinished.get("activePrivilegedRequests"));
        assertEquals(SECONDS.toMillis(60), afterOldestFinished.get("oldestActivePrivilegedRequestMillis"));

        runtime.privilegedRequestFinished(second);
        Map<String, Object> idle = runtime.statusAt("127.0.0.1", 9443, STARTED_AT, SECONDS.toNanos(90));
        assertEquals(0, idle.get("activePrivilegedRequests"));
        assertEquals(0L, idle.get("oldestActivePrivilegedRequestMillis"));
        assertEquals(2L, idle.get("totalPrivilegedRequests"), "completed privileged work stays in the total");
    }

    /** A privileged refusal must remain visible in the aggregate an operator alerts on. */
    @Test
    void aThrottledPrivilegedRequestCountsInBothTheAggregateAndThePrivilegedCounter() {
        MorpheusRemoteRuntimeState runtime = state(STARTED_AT);

        runtime.recordThrottledRequest();
        runtime.recordThrottledPrivilegedRequest();

        Map<String, Object> status = runtime.statusAt("127.0.0.1", 9443, STARTED_AT);
        assertEquals(2L, status.get("throttledRequests"));
        assertEquals(1L, status.get("throttledPrivilegedRequests"));
    }

    /** A ticket given back twice, or one that was never issued, must not corrupt the gauge. */
    @Test
    void anUnknownOrRepeatedPrivilegedTicketLeavesTheGaugeIntact() {
        MorpheusRemoteRuntimeState runtime = state(STARTED_AT);
        long ticket = runtime.privilegedRequestStarted(0L);

        runtime.privilegedRequestFinished(ticket);
        runtime.privilegedRequestFinished(ticket);
        runtime.privilegedRequestFinished(Long.MIN_VALUE);

        assertEquals(0, runtime.statusAt("127.0.0.1", 9443, STARTED_AT).get("activePrivilegedRequests"));
    }

    @Test
    void rejectsNullStatusInputs() {
        MorpheusRemoteRuntimeState runtime = state(STARTED_AT);

        assertThrows(NullPointerException.class, () -> runtime.statusAt(null, 443, STARTED_AT));
        assertThrows(NullPointerException.class, () -> runtime.statusAt("localhost", 443, null));
    }

    private static MorpheusRemoteRuntimeState state(Instant startedAt) {
        return new MorpheusRemoteRuntimeState(
                4,
                2,
                Duration.ofSeconds(15),
                16 * 1024 * 1024,
                128 * 1024 * 1024,
                8,
                startedAt);
    }
}
