package com.morpheus.api;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Bounds how long a downstream client can keep a response -- and the resources behind it -- open.
 *
 * <p>Every other budget on the remote facade is taken before the work starts: concurrency permits, the response
 * memory budget, the request-body deadline. They are all released in a {@code finally} that only runs when the
 * handler returns, and the handler returns when the last byte has been written. A client that completes the TLS
 * handshake, authenticates, sends a valid request and then simply stops reading never lets that happen: the
 * write blocks on a full socket, the permits stay taken, and enough such clients close the facade to everyone
 * else without a single malformed byte. Bounding the response body's size does not help, because the cost is
 * time, not memory.</p>
 *
 * <h2>How the deadline is enforced</h2>
 *
 * <p>{@code jdk.httpserver} exposes no write timeout, and its only response deadline is the undocumented
 * {@code sun.net.httpserver.maxRspTime} system property, which MORPHEUS does not rely on. What it does write
 * through is a blocking {@link java.nio.channels.SocketChannel}, and that is specified: a channel implementing
 * {@link java.nio.channels.InterruptibleChannel} closes and raises
 * {@link java.nio.channels.ClosedByInterruptException} when the thread blocked on it is interrupted. So the
 * deadline is enforced by interrupting the writing thread, and the guarantee is the documented contract of
 * {@code java.nio.channels}, not an internal property.</p>
 *
 * <p>The write runs on the calling thread rather than on a borrowed one. Handing it to another thread would
 * leave {@code exchange.close()} racing a still-blocked writer for the response stream's monitor, which is the
 * one place the cure could hang worse than the disease.</p>
 *
 * <h2>Two budgets, because "slow" and "stopped" are different</h2>
 *
 * <p>A stall budget rearms on every chunk that reaches the socket, so a genuinely slow reader is served for as
 * long as it keeps making progress. A total budget bounds the whole response, so a client that trickles one
 * byte per stall window forever is bounded too.</p>
 *
 * <h2>Residual limitation</h2>
 *
 * <p>The response is aborted, not gracefully completed: the client sees a truncated body on a closed connection,
 * and no error envelope can be delivered, because the connection that would carry it is the resource being
 * reclaimed. That is the honest outcome -- the alternative is holding the slot until the client relents.</p>
 */
final class TimedBoundedResponseWriter implements AutoCloseable {
    static final Duration RESPONSE_STALL_TIMEOUT = Duration.ofSeconds(15);
    static final Duration RESPONSE_TOTAL_TIMEOUT = Duration.ofSeconds(120);

    private final ScheduledExecutorService watchdog;
    private final long stallNanos;
    private final long totalNanos;
    private final long tickMillis;

    TimedBoundedResponseWriter() {
        this(RESPONSE_STALL_TIMEOUT, RESPONSE_TOTAL_TIMEOUT);
    }

    TimedBoundedResponseWriter(Duration stallTimeout, Duration totalTimeout) {
        requirePositive(stallTimeout, "stallTimeout");
        requirePositive(totalTimeout, "totalTimeout");
        if (stallTimeout.compareTo(totalTimeout) > 0) {
            throw new IllegalArgumentException("stallTimeout must not exceed totalTimeout");
        }
        this.stallNanos = stallTimeout.toNanos();
        this.totalNanos = totalTimeout.toNanos();
        this.tickMillis = Math.max(1L, stallTimeout.toMillis() / 4);
        this.watchdog = Executors.newSingleThreadScheduledExecutor(watchdogThreads());
    }

    /**
     * Runs {@code body} under the deadline, on the calling thread.
     *
     * @throws ResponseWriteTimeoutException when the client stopped draining the response and the connection was
     *                                       reclaimed; the exchange is unusable afterwards
     */
    void write(ResponseBody body) throws IOException {
        Objects.requireNonNull(body, "body");
        Deadline deadline = new Deadline(Thread.currentThread(), System.nanoTime(), stallNanos, totalNanos);
        ScheduledFuture<?> alarm = watchdog.scheduleAtFixedRate(
                deadline::checkExpiry, tickMillis, tickMillis, TimeUnit.MILLISECONDS);
        try {
            body.writeTo(deadline);
        } catch (IOException failure) {
            if (deadline.expired()) {
                throw new ResponseWriteTimeoutException("response write exceeded its client deadline", failure);
            }
            throw failure;
        } finally {
            alarm.cancel(false);
            // settle() returns under the same monitor the alarm interrupts from, so a true answer means the
            // interrupt has already been delivered and can be cleared here rather than surfacing on the next
            // blocking call this thread makes.
            if (deadline.settle()) Thread.interrupted();
        }
    }

    @Override
    public void close() {
        watchdog.shutdownNow();
    }

    /** A response body written against a deadline that {@link Progress#made()} rearms. */
    @FunctionalInterface
    interface ResponseBody {
        void writeTo(Progress progress) throws IOException;
    }

    /** Reports that bytes reached the client, which rearms the stall budget. */
    @FunctionalInterface
    interface Progress {
        void made();
    }

    static final class ResponseWriteTimeoutException extends IOException {
        private ResponseWriteTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static ThreadFactory watchdogThreads() {
        return runnable -> {
            Thread thread = new Thread(runnable, "morpheus-remote-response-deadline");
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    /**
     * The deadline of one response, and the only thing that may interrupt its writer.
     *
     * <p>Expiry and settlement are both taken under this object's monitor, and the interrupt is delivered while
     * that monitor is held. That is what makes the race decidable: once {@link #settle()} has been entered the
     * alarm can no longer fire, and if it already did, the interrupt it sent has already been delivered.</p>
     */
    private static final class Deadline implements Progress {
        private final Thread writer;
        private final long startNanos;
        private final long stallNanos;
        private final long totalNanos;
        private volatile long lastProgressNanos;
        private boolean expired;
        private boolean settled;

        private Deadline(Thread writer, long startNanos, long stallNanos, long totalNanos) {
            this.writer = writer;
            this.startNanos = startNanos;
            this.stallNanos = stallNanos;
            this.totalNanos = totalNanos;
            this.lastProgressNanos = startNanos;
        }

        @Override
        public void made() {
            lastProgressNanos = System.nanoTime();
        }

        synchronized void checkExpiry() {
            if (settled || expired) return;
            long now = System.nanoTime();
            if (now - lastProgressNanos < stallNanos && now - startNanos < totalNanos) return;
            expired = true;
            writer.interrupt();
        }

        synchronized boolean expired() {
            return expired;
        }

        /** Ends the deadline; returns whether an interrupt was delivered to the writer. */
        synchronized boolean settle() {
            settled = true;
            return expired;
        }
    }
}
