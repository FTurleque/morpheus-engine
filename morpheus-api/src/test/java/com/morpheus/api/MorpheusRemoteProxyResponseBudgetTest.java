package com.morpheus.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The response budget bounds memory in flight, not how long an upstream operation takes.
 *
 * <p>The slot was taken before the loopback call and held for its whole duration, so a mutation that never
 * answers held one while no response bytes existed at all. A write has no upstream deadline — deliberately,
 * because a deadline over a commit that has already become durable would answer 504 for work that happened —
 * so blocked writes kept their slots indefinitely. There are eight, and a remote server admits sixteen
 * concurrent writes by default: eight stuck mutations shut the whole facade, reads included.</p>
 *
 * <p>What bounds mutations is {@code privilegedConcurrency}, which exists for exactly that. The response
 * budget is taken once the upstream headers are in, which is the first moment a response body can occupy
 * memory, and released on every exit.</p>
 */
class MorpheusRemoteProxyResponseBudgetTest {

    private HttpServer upstream;
    private ExecutorService callers;
    private final CountDownLatch upstreamBlocked = new CountDownLatch(2);
    private final CountDownLatch releaseUpstream = new CountDownLatch(1);

    @BeforeEach
    void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/api/v1/projects", exchange -> {
            upstreamBlocked.countDown();
            try {
                releaseUpstream.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, "{\"data\":\"written\"}");
        });
        upstream.createContext("/api/v1/health", exchange -> respond(exchange, "{\"data\":\"ok\"}"));
        upstream.setExecutor(Executors.newFixedThreadPool(4));
        upstream.start();
        callers = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void stopUpstream() {
        releaseUpstream.countDown();
        callers.shutdownNow();
        upstream.stop(0);
    }

    @Test
    void blockedMutationsDoNotConsumeTheResponseBudgetAReadNeeds() throws Exception {
        Semaphore responseSlots = new Semaphore(2, true);
        MorpheusRemoteProxyTransport transport = transport(responseSlots);

        // Two writes that never answer. They have no upstream deadline, by design.
        List<Future<?>> writes = List.of(
                callers.submit(() -> forward(transport, "POST", "/api/v1/projects")),
                callers.submit(() -> forward(transport, "POST", "/api/v1/projects")));
        assertTrue(upstreamBlocked.await(10, TimeUnit.SECONDS), "both mutations must have reached the upstream");

        // A read whose own concurrency is free must still be served: the stuck mutations hold no response memory.
        StubExchange read = new StubExchange("GET", "/api/v1/health");
        assertNull(forward(transport, read), "the read must not be refused while mutations are merely waiting");
        assertEquals(200, read.responseCode());
        assertEquals("{\"data\":\"ok\"}", read.body());

        releaseUpstream.countDown();
        for (Future<?> write : writes) {
            assertNull(write.get(10, TimeUnit.SECONDS), "the mutations must complete once the upstream answers");
        }
        assertEquals(2, responseSlots.availablePermits(), "every slot must be back once the calls finish");
    }

    /** A read is refused before the call: nothing happened upstream, so a retry costs the caller nothing else. */
    @Test
    void aSaturatedBudgetRefusesAReadBeforeTheCallIsMade() {
        Semaphore exhausted = new Semaphore(0);
        MorpheusRemoteProxyTransport transport = transport(exhausted);

        MorpheusRemoteProxyTransport.TransportException failure =
                (MorpheusRemoteProxyTransport.TransportException)
                        forward(transport, new StubExchange("GET", "/api/v1/health"));

        assertEquals(429, failure.status());
        assertEquals("RESPONSE_BUDGET_EXHAUSTED", failure.code());
        assertEquals(0, exhausted.availablePermits(), "a refused call must not have taken a permit");
    }

    /**
     * A mutation is never refused for want of budget, because by the time the upstream has answered its commit
     * has already happened. Refusing there would report a failure for work that is durable, which is the same
     * lie as putting a deadline on it. It waits instead.
     */
    @Test
    void aMutationWaitsForTheBudgetRatherThanBeingRefusedAfterItsCommit() throws Exception {
        Semaphore exhausted = new Semaphore(0, true);
        MorpheusRemoteProxyTransport transport = transport(exhausted);
        releaseUpstream.countDown();

        StubExchange write = new StubExchange("POST", "/api/v1/projects");
        Future<Object> pending = callers.submit(() -> forward(transport, write));

        // It must not have answered anything yet: no budget, and no refusal either.
        assertThrows(TimeoutException.class, () -> pending.get(300, TimeUnit.MILLISECONDS));

        exhausted.release();
        assertNull(pending.get(10, TimeUnit.SECONDS), "the mutation must complete once a slot frees up");
        assertEquals(200, write.responseCode());
        assertEquals(1, exhausted.availablePermits(), "the slot must be given back");
    }

    /**
     * An interrupt while a mutation waits for the budget must not abandon the upstream response. Nobody will
     * read it now, so its connection goes back to the pool rather than being held until the client is collected.
     */
    @Test
    void anInterruptWhileWaitingForTheBudgetClosesTheUpstreamResponse() throws Exception {
        Semaphore exhausted = new Semaphore(0, true);
        MorpheusRemoteProxyTransport transport = transport(exhausted);
        releaseUpstream.countDown();

        AtomicReference<Object> outcome = new AtomicReference<>();
        StubExchange write = new StubExchange("POST", "/api/v1/projects");
        Thread caller = new Thread(() -> outcome.set(forward(transport, write)));
        caller.start();
        awaitBudgetWait(exhausted);

        caller.interrupt();
        caller.join(TimeUnit.SECONDS.toMillis(10));

        assertTrue(outcome.get() instanceof MorpheusRemoteProxyTransport.TransportException,
                () -> "the interrupted mutation must fail explicitly, got: " + outcome.get());
        MorpheusRemoteProxyTransport.TransportException failure =
                (MorpheusRemoteProxyTransport.TransportException) outcome.get();
        assertEquals(503, failure.status());
        assertEquals("UPSTREAM_INTERRUPTED", failure.code());
        assertEquals(-1, write.responseCode(), "nothing must have been written downstream");
        assertEquals(0, exhausted.availablePermits(), "a budget that was never taken must not be given back");
    }

    /** The upstream has answered and the caller is parked on the saturated budget. */
    private static void awaitBudgetWait(Semaphore budget) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!budget.hasQueuedThreads()) {
            assertTrue(System.nanoTime() < deadline, "the mutation must have reached the budget wait");
            Thread.sleep(10);
        }
    }

    @Test
    void theSlotIsReleasedWhenTheUpstreamAnswersSomethingUnusable() throws Exception {
        upstream.createContext("/api/v1/unbounded", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        Semaphore responseSlots = new Semaphore(1, true);
        MorpheusRemoteProxyTransport transport = transport(responseSlots);

        Object outcome = forward(transport, new StubExchange("GET", "/api/v1/unbounded"));

        assertTrue(outcome instanceof MorpheusRemoteProxyTransport.TransportException,
                () -> "a chunked upstream response must be refused, got: " + outcome);
        assertEquals(1, responseSlots.availablePermits(), "the slot must come back after an unusable response");
    }

    private MorpheusRemoteProxyTransport transport(Semaphore responseSlots) {
        return new MorpheusRemoteProxyTransport(
                MorpheusInternalCapability.generate(),
                new MorpheusRemoteRuntimeState(4, Duration.ofSeconds(1), 4096, 4096, 2, Instant.EPOCH),
                4096,
                responseSlots,
                HttpClient.newHttpClient());
    }

    /** Returns the failure the call produced, or null when it completed. */
    private Object forward(MorpheusRemoteProxyTransport transport, String method, String path) {
        return forward(transport, new StubExchange(method, path));
    }

    private Object forward(MorpheusRemoteProxyTransport transport, StubExchange exchange) {
        try {
            transport.forward(
                    exchange,
                    URI.create("http://127.0.0.1:" + upstream.getAddress().getPort() + exchange.path),
                    new byte[0],
                    MorpheusRemoteHttpServer.usesBoundedUpstreamTimeout(exchange.getRequestMethod(), exchange.path));
            return null;
        } catch (IOException | RuntimeException failure) {
            return failure;
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static final class StubExchange extends HttpExchange {
        private final String method;
        private final String path;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private final AtomicReference<Integer> code = new AtomicReference<>(-1);

        private StubExchange(String method, String path) {
            this.method = method;
            this.path = path;
        }

        String body() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }

        int responseCode() {
            return code.get();
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return URI.create(path);
        }

        @Override
        public String getRequestMethod() {
            return method;
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return InputStream.nullInputStream();
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            code.set(rCode);
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }

        @Override
        public int getResponseCode() {
            return code.get();
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8443);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream input, OutputStream output) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
