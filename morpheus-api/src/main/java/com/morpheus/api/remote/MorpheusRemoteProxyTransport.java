package com.morpheus.api.remote;

import com.morpheus.api.local.MorpheusInternalCapability;
import com.morpheus.api.transport.TimedBoundedResponseWriter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.Semaphore;

/** Bounded loopback HTTP transport used by the remote HTTPS facade after authorization and target resolution. */
final class MorpheusRemoteProxyTransport {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final MorpheusInternalCapability internalCapability;
    private final MorpheusRemoteRuntimeState runtime;
    private final int maxResponseBytes;
    private final Semaphore responseSlots;
    private final HttpClient client;
    private final TimedBoundedResponseWriter bounded;

    MorpheusRemoteProxyTransport(
            MorpheusInternalCapability internalCapability,
            MorpheusRemoteRuntimeState runtime,
            int maxResponseBytes,
            int maxResponseSlots,
            TimedBoundedResponseWriter bounded) {
        this(
                internalCapability,
                runtime,
                maxResponseBytes,
                new Semaphore(maxResponseSlots, true),
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                bounded);
    }

    MorpheusRemoteProxyTransport(
            MorpheusInternalCapability internalCapability,
            MorpheusRemoteRuntimeState runtime,
            int maxResponseBytes,
            Semaphore responseSlots,
            HttpClient client,
            TimedBoundedResponseWriter bounded) {
        this.internalCapability = Objects.requireNonNull(internalCapability, "internalCapability");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        if (maxResponseBytes < 1) throw new IllegalArgumentException("maxResponseBytes must be positive");
        this.maxResponseBytes = maxResponseBytes;
        this.responseSlots = Objects.requireNonNull(responseSlots, "responseSlots");
        this.client = Objects.requireNonNull(client, "client");
        this.bounded = Objects.requireNonNull(bounded, "bounded");
    }

    void forward(
            HttpExchange exchange,
            URI target,
            byte[] requestBody,
            UpstreamDeadline deadline) throws IOException {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(requestBody, "requestBody");
        Objects.requireNonNull(deadline, "deadline");

        String upstreamMethod = exchange.getRequestMethod();
        HttpRequest request = upstreamRequest(exchange, target, requestBody, upstreamMethod, deadline.timeout());

        // A read is refused before the call: nothing has happened upstream yet, so failing fast costs the
        // caller a retry and nothing else.
        if (deadline.readOnly() && !responseSlots.tryAcquire()) {
            runtime.recordThrottledRequest();
            throw new TransportException(
                    429,
                    "RESPONSE_BUDGET_EXHAUSTED",
                    "remote proxy response memory budget is saturated");
        }
        boolean budgetHeld = deadline.readOnly();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (!budgetHeld) {
                awaitResponseBudget(response);
                budgetHeld = true;
            }
            relay(exchange, upstreamMethod, response);
        } catch (HttpTimeoutException timeout) {
            throw deadline.expired();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TransportException(
                    503,
                    "UPSTREAM_INTERRUPTED",
                    "local MORPHEUS API proxy was interrupted");
        } finally {
            if (budgetHeld) {
                responseSlots.release();
            }
        }
    }

    private HttpRequest upstreamRequest(
            HttpExchange exchange, URI target, byte[] requestBody, String upstreamMethod, Duration timeout) {
        HttpRequest.Builder request = HttpRequest.newBuilder(target);
        internalCapability.authorize(request);
        request.timeout(timeout);

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null) request.header("Content-Type", contentType);
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        if (accept != null) request.header("Accept", accept);

        request.method(upstreamMethod, requestBody.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(requestBody));
        return request.build();
    }

    /** Writes the upstream response downstream, within the size bound and the response deadlines. */
    private void relay(HttpExchange exchange, String upstreamMethod, HttpResponse<InputStream> response)
            throws IOException {
        try (InputStream upstream = response.body()) {
            String responseType = response.headers().firstValue("Content-Type")
                    .orElse("application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Type", responseType);
            response.headers().firstValue("Allow")
                    .ifPresent(value -> exchange.getResponseHeaders().set("Allow", value));

            if (isBodyless(upstreamMethod, response.statusCode())) {
                response.headers().firstValue("Content-Length")
                        .ifPresent(value -> exchange.getResponseHeaders().set("Content-Length", value));
                bounded.write(progress -> {
                    exchange.sendResponseHeaders(response.statusCode(), -1);
                    progress.made();
                });
                return;
            }

            long declaredLength = requireBoundedLength(
                    response.headers().firstValueAsLong("Content-Length"), maxResponseBytes);
            bounded.write(progress -> {
                exchange.sendResponseHeaders(response.statusCode(), declaredLength);
                copyBounded(upstream, exchange.getResponseBody(), declaredLength, maxResponseBytes, progress);
            });
        }
    }

    /**
     * Takes the response budget for a call that may change state, once the upstream has answered.
     *
     * <p>Both halves of that matter. Held across the upstream wait, the budget would be kept for up to the writes'
     * deadline by calls that have no response bytes yet: there are eight slots for a server that admits sixteen
     * concurrent mutations, so eight slow mutations would refuse every read for minutes. Refusing here instead of
     * waiting would lie: the commit has already happened by the time these headers exist. The wait itself ends,
     * because every holder of a slot writes its response under the deadlines of {@link TimedBoundedResponseWriter}
     * and gives the slot back.</p>
     */
    private void awaitResponseBudget(HttpResponse<InputStream> response) throws InterruptedException, IOException {
        try {
            responseSlots.acquire();
        } catch (InterruptedException interrupted) {
            // The response exists and nobody will read it now, so its connection goes back to the pool rather
            // than being held until the client is collected.
            response.body().close();
            throw interrupted;
        }
    }

    static boolean isBodyless(String method, int status) {
        return method.equalsIgnoreCase("HEAD") || status == 204 || status == 304;
    }

    static long requireBoundedLength(OptionalLong declaredLength, int maxResponseBytes) {
        if (declaredLength.isEmpty() || declaredLength.getAsLong() < 0) {
            throw new TransportException(
                    502,
                    "UPSTREAM_LENGTH_REQUIRED",
                    "local MORPHEUS response omitted a bounded Content-Length");
        }
        long length = declaredLength.getAsLong();
        if (length > maxResponseBytes) {
            throw new TransportException(
                    502,
                    "UPSTREAM_RESPONSE_TOO_LARGE",
                    "local MORPHEUS response exceeds " + maxResponseBytes + " bytes");
        }
        return length;
    }

    /**
     * Streams the upstream body downstream, reporting each chunk that reached the client.
     *
     * <p>The progress report is what separates a slow reader from a stopped one: it rearms the stall budget of
     * {@link TimedBoundedResponseWriter}, so a client that keeps draining is served for as long as it does.</p>
     */
    static void copyBounded(
            InputStream upstream,
            OutputStream downstream,
            long declaredLength,
            int maxResponseBytes,
            TimedBoundedResponseWriter.Progress progress) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = upstream.read(buffer)) != -1) {
            if (total + read > declaredLength || total + read > maxResponseBytes) {
                throw new IOException("local MORPHEUS response exceeded its declared or configured bound");
            }
            downstream.write(buffer, 0, read);
            progress.made();
            total += read;
        }
        if (total != declaredLength) {
            throw new IOException("local MORPHEUS response length changed while proxying");
        }
    }

    /**
     * How long the facade waits for the local server's answer to one call, and what an expiry tells the caller.
     *
     * <p>{@code readOnly} says whether the call can have changed anything. A read is refused for want of response
     * budget before it is sent, and its expiry is {@code UPSTREAM_TIMEOUT}: retrying it is safe. A call that may
     * change state takes its budget once answered, and its expiry is {@code UPSTREAM_WRITE_TIMEOUT}: the local
     * server cannot cancel an operation it has started, so the outcome is unknown and is never reported as a
     * failure.</p>
     *
     * <p>On expiry the facade gives up, answers 504, and the request gives back its request slot and its privileged
     * permit, while the local operation may still be running and holding the database. The privileged ceiling
     * therefore bounds the calls the facade is waiting for, not the work in progress; the admission control of the
     * local server bounds that work.</p>
     */
    record UpstreamDeadline(Duration timeout, boolean readOnly) {
        /** A read: sixty seconds. */
        static final UpstreamDeadline READ = new UpstreamDeadline(Duration.ofSeconds(60), true);

        /**
         * A write or an administration call: three hundred seconds. The only duration the repository states for the
         * longest work a write can trigger is the frozen budget of a full publish on the large fixture, sixty
         * seconds at the 95th percentile ({@code M19FullPublishPerformanceGate}); three hundred seconds gives it
         * five times over. Sixty seconds, the reads' deadline, would leave a publish at its budget no margin.
         */
        static final UpstreamDeadline WRITE = new UpstreamDeadline(Duration.ofSeconds(300), false);

        UpstreamDeadline {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("upstream timeout must be positive");
            }
        }

        private TransportException expired() {
            if (readOnly) {
                return new TransportException(
                        504,
                        "UPSTREAM_TIMEOUT",
                        "local MORPHEUS read-only operation exceeded its timeout");
            }
            return new TransportException(
                    504,
                    "UPSTREAM_WRITE_TIMEOUT",
                    "local MORPHEUS operation exceeded the remote facade's timeout; it may have completed: "
                            + "read its state before retrying");
        }
    }

    static final class TransportException extends RuntimeException {
        private final int status;
        private final String code;

        private TransportException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        int status() {
            return status;
        }

        String code() {
            return code;
        }
    }
}
