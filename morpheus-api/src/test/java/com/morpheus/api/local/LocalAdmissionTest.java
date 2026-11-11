package com.morpheus.api.local;

import com.morpheus.api.BoundedWait;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * More simultaneous requests than the database can serve wait their turn, and a wait that does not end is refused.
 *
 * <p>The local server admitted every request at once: each opened a connection and they contended for the database,
 * so a burst from one user - an assistant firing several tools - came back as busy failures. Asserted here through a
 * real server with two workers, so that the ceiling is reached with three requests.</p>
 */
class LocalAdmissionTest {
    private static final int WORKERS = 2;
    private static final Duration WAIT = Duration.ofMillis(400);

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger inside = new AtomicInteger();
    private final AtomicInteger mostInsideAtOnce = new AtomicInteger();
    private ExecutorService requestThreads;
    private DatabaseWorkOffloadingHttpServer server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void start() throws IOException {
        HttpServer delegate = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        requestThreads = Executors.newVirtualThreadPerTaskExecutor();
        delegate.setExecutor(requestThreads);
        server = DatabaseWorkOffloadingHttpServer.over(delegate, WORKERS, WAIT);
        server.createContext("/held", exchange -> {
            entered();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            left();
            answer(exchange, "held");
        });
        server.createContext("/quick", exchange -> {
            entered();
            left();
            answer(exchange, "quick");
        });
        server.createContext("/failing", exchange -> {
            throw new IllegalStateException("a handler that fails still gives its worker back");
        });
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        release.countDown();
        client.close();
        server.stop(0);
        requestThreads.shutdownNow();
    }

    @Test
    void aBurstBeyondTheWorkersIsAnsweredInFullAndNeverMoreThanTheWorkersAreInsideAtOnce() throws Exception {
        List<CompletableFuture<HttpResponse<String>>> burst = new ArrayList<>();
        for (int number = 0; number < 12 * WORKERS; number++) {
            burst.add(send("/quick"));
        }

        for (CompletableFuture<HttpResponse<String>> response : burst) {
            assertEquals(200, response.get(30, TimeUnit.SECONDS).statusCode());
        }
        assertTrue(mostInsideAtOnce.get() <= WORKERS, () -> "inside at once: " + mostInsideAtOnce.get());
    }

    @Test
    void aRequestThatWaitedItsBoundWithoutAWorkerIsRefusedByNameWithARetryAfter() throws Exception {
        List<CompletableFuture<HttpResponse<String>>> holding = List.of(send("/held"), send("/held"));
        awaitInside(WORKERS);

        long started = System.nanoTime();
        HttpResponse<String> refused = send("/quick").get(30, TimeUnit.SECONDS);
        long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals(429, refused.statusCode(), refused.body());
        assertTrue(refused.body().contains("\"code\":\"" + DatabaseWorkOffloadingHttpServer.BUSY_CODE + "\""),
                refused.body());
        assertEquals(DatabaseWorkOffloadingHttpServer.RETRY_AFTER_SECONDS,
                refused.headers().firstValue("Retry-After").orElse(""));
        assertEquals("nosniff", refused.headers().firstValue("X-Content-Type-Options").orElse(""),
                "a refusal carries the headers of every local response");
        assertTrue(waitedMillis >= WAIT.toMillis() - 50, () -> "refused after " + waitedMillis + " ms");

        release.countDown();
        for (CompletableFuture<HttpResponse<String>> held : holding) {
            assertEquals(200, held.get(30, TimeUnit.SECONDS).statusCode());
        }
        assertEquals(200, send("/quick").get(30, TimeUnit.SECONDS).statusCode(), "a worker freed is a worker given");
    }

    @Test
    void aHandlerThatFailsGivesItsPlaceBack() throws Exception {
        for (int number = 0; number < 3 * WORKERS; number++) {
            send("/failing").handle((response, failure) -> null).get(30, TimeUnit.SECONDS);
        }

        assertEquals(200, send("/quick").get(30, TimeUnit.SECONDS).statusCode(),
                "the places of failed requests were returned, or this one would wait and be refused");
    }

    private CompletableFuture<HttpResponse<String>> send(String path) {
        return client.sendAsync(
                HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void entered() {
        mostInsideAtOnce.accumulateAndGet(inside.incrementAndGet(), Math::max);
    }

    private void left() {
        inside.decrementAndGet();
    }

    private void awaitInside(int expected) throws InterruptedException {
        BoundedWait.until(
                "every worker to be handling a request",
                Duration.ofSeconds(10),
                BoundedWait.LOCAL_COUNTER_POLL,
                () -> inside.get() >= expected,
                () -> "requests inside=" + inside.get() + " of " + expected);
    }

    private static void answer(com.sun.net.httpserver.HttpExchange exchange, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
