package com.morpheus.api;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe operational counters and status snapshot for the remote HTTPS facade.
 *
 * <p>This component deliberately contains no authentication or authorization policy. It only records
 * transport/runtime outcomes already decided by {@link MorpheusRemoteHttpServer}.</p>
 */
final class MorpheusRemoteRuntimeState {
    private final Instant startedAt;
    private final int maxConcurrentRequests;
    private final Duration requestBodyReadTimeout;
    private final int maxProxyResponseBytes;
    private final int maxProxyInFlightBytes;
    private final int maxConcurrentBufferedProxyResponses;
    private final AtomicInteger activeRequests = new AtomicInteger();
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder authenticationFailures = new LongAdder();
    private final LongAdder authorizationFailures = new LongAdder();
    private final LongAdder throttledRequests = new LongAdder();
    private final LongAdder requestTimeouts = new LongAdder();

    MorpheusRemoteRuntimeState(
            int maxConcurrentRequests,
            Duration requestBodyReadTimeout,
            int maxProxyResponseBytes,
            int maxProxyInFlightBytes,
            int maxConcurrentBufferedProxyResponses) {
        this(
                maxConcurrentRequests,
                requestBodyReadTimeout,
                maxProxyResponseBytes,
                maxProxyInFlightBytes,
                maxConcurrentBufferedProxyResponses,
                Instant.now());
    }

    MorpheusRemoteRuntimeState(
            int maxConcurrentRequests,
            Duration requestBodyReadTimeout,
            int maxProxyResponseBytes,
            int maxProxyInFlightBytes,
            int maxConcurrentBufferedProxyResponses,
            Instant startedAt) {
        if (maxConcurrentRequests < 1) throw new IllegalArgumentException("maxConcurrentRequests must be positive");
        if (maxProxyResponseBytes < 1) throw new IllegalArgumentException("maxProxyResponseBytes must be positive");
        if (maxProxyInFlightBytes < 1) throw new IllegalArgumentException("maxProxyInFlightBytes must be positive");
        if (maxConcurrentBufferedProxyResponses < 1) {
            throw new IllegalArgumentException("maxConcurrentBufferedProxyResponses must be positive");
        }
        this.maxConcurrentRequests = maxConcurrentRequests;
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

    void recordAuthenticationFailure() {
        authenticationFailures.increment();
    }

    void recordAuthorizationFailure() {
        authorizationFailures.increment();
    }

    void recordThrottledRequest() {
        throttledRequests.increment();
    }

    void recordRequestTimeout() {
        requestTimeouts.increment();
    }

    Map<String, Object> status(String host, int port) {
        return statusAt(host, port, Instant.now());
    }

    Map<String, Object> statusAt(String host, int port, Instant now) {
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
        status.put("requestBodyReadTimeoutMillis", requestBodyReadTimeout.toMillis());
        status.put("maxProxyResponseBytes", maxProxyResponseBytes);
        status.put("maxProxyInFlightBytes", maxProxyInFlightBytes);
        status.put("maxConcurrentBufferedProxyResponses", maxConcurrentBufferedProxyResponses);
        status.put("totalRequests", totalRequests.sum());
        status.put("authenticationFailures", authenticationFailures.sum());
        status.put("authorizationFailures", authorizationFailures.sum());
        status.put("throttledRequests", throttledRequests.sum());
        status.put("requestTimeouts", requestTimeouts.sum());
        return Map.copyOf(status);
    }

    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
