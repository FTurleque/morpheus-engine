package com.morpheus.api;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe operational counters and status snapshot for the remote HTTPS facade.
 *
 * <p>This component deliberately contains no authentication or authorization policy. It only records
 * transport/runtime outcomes already decided by {@link MorpheusRemoteHttpServer}.</p>
 *
 * <p>Privileged operations are tracked apart from ordinary requests. A mutation carries no upstream deadline
 * by design, so a blocked one stays active indefinitely and is invisible inside an aggregate request count
 * that also moves with normal read traffic. The privileged gauge and the age of the oldest privileged
 * operation are what separate a busy facade from a stuck one.</p>
 */
final class MorpheusRemoteRuntimeState {
    private final Instant startedAt;
    private final int maxConcurrentRequests;
    private final int maxConcurrentPrivilegedRequests;
    private final Duration requestBodyReadTimeout;
    private final int maxProxyResponseBytes;
    private final int maxProxyInFlightBytes;
    private final int maxConcurrentBufferedProxyResponses;
    private final AtomicInteger activeRequests = new AtomicInteger();
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder authenticationFailures = new LongAdder();
    private final LongAdder authorizationFailures = new LongAdder();
    private final LongAdder throttledRequests = new LongAdder();
    private final LongAdder throttledPrivilegedRequests = new LongAdder();
    private final LongAdder requestTimeouts = new LongAdder();
    private final LongAdder responseWriteTimeouts = new LongAdder();
    private final LongAdder totalPrivilegedRequests = new LongAdder();

    // The privileged semaphore gates every insertion, so this map cannot grow past
    // maxConcurrentPrivilegedRequests live entries.
    private final Map<Long, Long> activePrivilegedStartNanos = new ConcurrentHashMap<>();
    private final AtomicLong privilegedTickets = new AtomicLong();

    MorpheusRemoteRuntimeState(
            int maxConcurrentRequests,
            int maxConcurrentPrivilegedRequests,
            Duration requestBodyReadTimeout,
            int maxProxyResponseBytes,
            int maxProxyInFlightBytes,
            int maxConcurrentBufferedProxyResponses) {
        this(
                maxConcurrentRequests,
                maxConcurrentPrivilegedRequests,
                requestBodyReadTimeout,
                maxProxyResponseBytes,
                maxProxyInFlightBytes,
                maxConcurrentBufferedProxyResponses,
                Instant.now());
    }

    MorpheusRemoteRuntimeState(
            int maxConcurrentRequests,
            int maxConcurrentPrivilegedRequests,
            Duration requestBodyReadTimeout,
            int maxProxyResponseBytes,
            int maxProxyInFlightBytes,
            int maxConcurrentBufferedProxyResponses,
            Instant startedAt) {
        if (maxConcurrentRequests < 1) throw new IllegalArgumentException("maxConcurrentRequests must be positive");
        if (maxConcurrentPrivilegedRequests < 1) {
            throw new IllegalArgumentException("maxConcurrentPrivilegedRequests must be positive");
        }
        if (maxConcurrentPrivilegedRequests > maxConcurrentRequests) {
            throw new IllegalArgumentException("maxConcurrentPrivilegedRequests must not exceed maxConcurrentRequests");
        }
        if (maxProxyResponseBytes < 1) throw new IllegalArgumentException("maxProxyResponseBytes must be positive");
        if (maxProxyInFlightBytes < 1) throw new IllegalArgumentException("maxProxyInFlightBytes must be positive");
        if (maxConcurrentBufferedProxyResponses < 1) {
            throw new IllegalArgumentException("maxConcurrentBufferedProxyResponses must be positive");
        }
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.maxConcurrentPrivilegedRequests = maxConcurrentPrivilegedRequests;
        this.requestBodyReadTimeout = requirePositive(requestBodyReadTimeout, "requestBodyReadTimeout");
        this.maxProxyResponseBytes = maxProxyResponseBytes;
        this.maxProxyInFlightBytes = maxProxyInFlightBytes;
        this.maxConcurrentBufferedProxyResponses = maxConcurrentBufferedProxyResponses;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }

    void recordRequest() {
        totalRequests.increment();
    }

    void requestStarted() {
        activeRequests.incrementAndGet();
    }

    void requestFinished() {
        int remaining = activeRequests.decrementAndGet();
        if (remaining < 0) {
            activeRequests.incrementAndGet();
            throw new IllegalStateException("remote active request counter underflow");
        }
    }

    /** Returns the ticket that {@link #privilegedRequestFinished(long)} must be given back. */
    long privilegedRequestStarted() {
        return privilegedRequestStarted(System.nanoTime());
    }

    long privilegedRequestStarted(long startNanos) {
        long ticket = privilegedTickets.incrementAndGet();
        totalPrivilegedRequests.increment();
        activePrivilegedStartNanos.put(ticket, startNanos);
        return ticket;
    }

    void privilegedRequestFinished(long ticket) {
        activePrivilegedStartNanos.remove(ticket);
    }

    void recordAuthenticationFailure() {
        authenticationFailures.increment();
    }

    void recordAuthorizationFailure() {
        authorizationFailures.increment();
    }

    void recordThrottledRequest() {
        throttledRequests.increment();
    }

    /**
     * A privileged refusal is also an ordinary refusal and is counted as both: the aggregate stays the number of
     * requests the facade turned away, while the privileged counter says how much of that was write/admin pressure.
     */
    void recordThrottledPrivilegedRequest() {
        throttledRequests.increment();
        throttledPrivilegedRequests.increment();
    }

    void recordRequestTimeout() {
        requestTimeouts.increment();
    }

    /**
     * A response the client stopped draining, whose connection the facade reclaimed.
     *
     * <p>It is counted apart from the request timeout because it says something different about the peer: a
     * request timeout is a client that stopped sending, and this is a client that stopped receiving. Only the
     * second one holds a slot for as long as the client stays connected, so a facade under this kind of pressure
     * looks busy for a reason no other counter explains.</p>
     */
    void recordResponseWriteTimeout() {
        responseWriteTimeouts.increment();
    }

    Map<String, Object> status(String host, int port) {
        return statusAt(host, port, Instant.now(), System.nanoTime());
    }

    Map<String, Object> statusAt(String host, int port, Instant now) {
        return statusAt(host, port, now, System.nanoTime());
    }

    Map<String, Object> statusAt(String host, int port, Instant now, long nowNanos) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(now, "now");
        long uptimeSeconds = Math.max(0, Duration.between(startedAt, now).toSeconds());
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("mode", "REMOTE");
        status.put("transport", "HTTPS");
        status.put("host", host);
        status.put("port", port);
        status.put("startedAt", startedAt.toString());
        status.put("uptimeSeconds", uptimeSeconds);
        status.put("activeRequests", activeRequests.get());
        status.put("maxConcurrentRequests", maxConcurrentRequests);
        status.put("activePrivilegedRequests", activePrivilegedStartNanos.size());
        status.put("maxConcurrentPrivilegedRequests", maxConcurrentPrivilegedRequests);
        status.put("oldestActivePrivilegedRequestMillis", oldestActivePrivilegedRequestMillis(nowNanos));
        status.put("requestBodyReadTimeoutMillis", requestBodyReadTimeout.toMillis());
        status.put("maxProxyResponseBytes", maxProxyResponseBytes);
        status.put("maxProxyInFlightBytes", maxProxyInFlightBytes);
        status.put("maxConcurrentBufferedProxyResponses", maxConcurrentBufferedProxyResponses);
        status.put("totalRequests", totalRequests.sum());
        status.put("totalPrivilegedRequests", totalPrivilegedRequests.sum());
        status.put("authenticationFailures", authenticationFailures.sum());
        status.put("authorizationFailures", authorizationFailures.sum());
        status.put("throttledRequests", throttledRequests.sum());
        status.put("throttledPrivilegedRequests", throttledPrivilegedRequests.sum());
        status.put("requestTimeouts", requestTimeouts.sum());
        status.put("responseWriteTimeouts", responseWriteTimeouts.sum());
        return Map.copyOf(status);
    }

    /**
     * Age of the privileged operation that has been running longest, or zero when none is running.
     *
     * <p>This is the number that tells a saturated facade from a stuck one: sixteen mutations seconds old are
     * load, and one mutation hours old is work that nothing will ever finish.</p>
     */
    private long oldestActivePrivilegedRequestMillis(long nowNanos) {
        long oldest = 0L;
        for (long startNanos : activePrivilegedStartNanos.values()) {
            long elapsed = nowNanos - startNanos;
            if (elapsed > oldest) oldest = elapsed;
        }
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(oldest));
    }

    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
