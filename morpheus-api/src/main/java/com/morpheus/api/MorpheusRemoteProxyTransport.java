package com.morpheus.api;

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
    private static final Duration READ_ONLY_UPSTREAM_TIMEOUT = Duration.ofSeconds(60);

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
            boolean boundedUpstreamTimeout) throws IOException {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(requestBody, "requestBody");

        HttpRequest.Builder request = HttpRequest.newBuilder(target);
        internalCapability.authorize(request);
        if (boundedUpstreamTimeout) request.timeout(READ_ONLY_UPSTREAM_TIMEOUT);

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null) request.header("Content-Type", contentType);
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        if (accept != null) request.header("Accept", accept);

        String upstreamMethod = exchange.getRequestMethod();
        request.method(upstreamMethod, requestBody.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(requestBody));

        // A read is refused before the call: nothing has happened upstream yet, so failing fast costs the
        // caller a retry and nothing else.
        if (boundedUpstreamTimeout && !responseSlots.tryAcquire()) {
            runtime.recordThrottledRequest();
            throw new TransportException(
                    429,
                    "RESPONSE_BUDGET_EXHAUSTED",
                    "remote proxy response memory budget is saturated");
        }
        boolean budgetHeld = boundedUpstreamTimeout;
        try {
            HttpResponse<InputStream> response = client.send(
                    request.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (!budgetHeld) {
                awaitResponseBudget(response);
                budgetHeld = true;
            }
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
        } catch (HttpTimeoutException timeout) {
            throw new TransportException(
                    504,
                    "UPSTREAM_TIMEOUT",
                    "local MORPHEUS read-only operation exceeded its timeout");
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

    /**
     * Takes the response budget for a mutation, once the upstream has answered.
     *
     * <p>Both halves of that matter. Holding the budget across the wait is what let blocked mutations shut the
     * facade: a mutation has no upstream deadline — deliberately, because a deadline over a commit that is
     * already durable would report 504 for work that happened — so it would hold a slot forever, and there are
     * eight for a server that admits sixteen concurrent mutations. Refusing here instead of waiting would be the
     * same lie in another form: the commit has already happened by the time these headers exist.</p>
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
