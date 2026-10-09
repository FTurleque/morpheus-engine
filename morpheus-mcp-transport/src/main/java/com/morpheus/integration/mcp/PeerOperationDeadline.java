package com.morpheus.integration.mcp;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The wall-clock bound of one operation towards an external MCP peer, and the envelope every peer integration must
 * fit in (ADR-0106, amendment of 8 October 2026).
 *
 * <p>The SDK bounds each request on its own. An operation is several of them in sequence -- {@code initialize},
 * {@code tools/list} (one request per page, with no limit on the pages), then one or more {@code tools/call} -- so
 * nothing bounded the operation, and the server's handler bound, set at twice one request, could expire first and
 * close the MCP session under a peer that never exceeded its own timeout.</p>
 *
 * <p>An operation now has a deadline of its request timeout times its sequential requests, plus
 * {@link #START_UP_ALLOWANCE}. When it passes, the watchdog runs the abort it was given -- closing the client, which
 * fails the pending request at once -- and the gateway reports the deadline rather than a broken session. The
 * envelope ({@link #MAX_REQUEST_TIMEOUT}, {@link #MAX_SEQUENTIAL_REQUESTS}) is what the server's handler bound is
 * derived from; {@link #of} refuses anything outside it, so a gateway cannot outgrow the bound by configuration.</p>
 */
public final class PeerOperationDeadline implements AutoCloseable {
    /** The longest request timeout any peer integration may be configured with. */
    public static final Duration MAX_REQUEST_TIMEOUT = Duration.ofSeconds(120);
    /** The most sequential requests one peer operation may send, {@code tools/list} counted as one page. */
    public static final int MAX_SEQUENTIAL_REQUESTS = 4;
    /** Process start-up and the local work around the requests, which no request timeout covers. */
    public static final Duration START_UP_ALLOWANCE = Duration.ofSeconds(15);
    /**
     * Closing a gateway after its operation: the SDK's graceful close waits up to ten seconds, then the transport
     * gives the peer two seconds to exit and two more after a forced kill.
     */
    public static final Duration CLOSE_ALLOWANCE = Duration.ofSeconds(15);
    /** The longest operation the envelope admits. */
    public static final Duration LONGEST_OPERATION = of(MAX_REQUEST_TIMEOUT, MAX_SEQUENTIAL_REQUESTS);

    private enum State { ARMED, DISARMED, EXPIRED }

    private final Duration deadline;
    private final AtomicReference<State> state = new AtomicReference<>(State.ARMED);
    private final Thread watchdog;

    private PeerOperationDeadline(Duration deadline, Runnable abort) {
        this.deadline = deadline;
        this.watchdog = Thread.ofVirtual().name("morpheus-peer-operation-deadline").start(() -> {
            try {
                Thread.sleep(deadline);
            } catch (InterruptedException disarmed) {
                return;
            }
            if (state.compareAndSet(State.ARMED, State.EXPIRED)) {
                try {
                    abort.run();
                } catch (RuntimeException ignored) {
                    // The abort only has to unblock the operation; the operation reports the deadline itself.
                }
            }
        });
    }

    /** The deadline of an operation of {@code sequentialRequests} requests, each bounded by {@code requestTimeout}. */
    public static Duration of(Duration requestTimeout, int sequentialRequests) {
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isNegative() || requestTimeout.isZero()
                || requestTimeout.compareTo(MAX_REQUEST_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "peer request timeout must be positive and at most " + MAX_REQUEST_TIMEOUT.toSeconds() + " s");
        }
        if (sequentialRequests < 1 || sequentialRequests > MAX_SEQUENTIAL_REQUESTS) {
            throw new IllegalArgumentException(
                    "a peer operation sends between 1 and " + MAX_SEQUENTIAL_REQUESTS + " sequential requests");
        }
        return requestTimeout.multipliedBy(sequentialRequests).plus(START_UP_ALLOWANCE);
    }

    /** Starts the clock; {@code abort} runs once if the operation is still running when {@code deadline} passes. */
    public static PeerOperationDeadline arm(Duration deadline, Runnable abort) {
        Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(abort, "abort");
        if (deadline.isNegative() || deadline.isZero()) {
            throw new IllegalArgumentException("peer operation deadline must be positive");
        }
        return new PeerOperationDeadline(deadline, abort);
    }

    public Duration deadline() {
        return deadline;
    }

    /** Whether the deadline passed while the operation was running, so its failure is the deadline's. */
    public boolean expired() {
        return state.get() == State.EXPIRED;
    }

    /** Stops the clock; after this the abort never runs. */
    @Override
    public void close() {
        if (state.compareAndSet(State.ARMED, State.DISARMED)) {
            watchdog.interrupt();
        }
    }
}
