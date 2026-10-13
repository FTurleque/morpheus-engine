package com.morpheus.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delegating server whose handlers run on a bounded pool of platform threads while the request's own thread waits.
 *
 * <p>Every request of the local server runs on a virtual thread. The SQLite driver is native code, and it waits for a
 * busy database inside the native call, where a virtual thread stays mounted on its carrier - whatever the JDK, since
 * only the pinning of monitors was lifted after Java 21. The carriers are as many as the processors. Measured on
 * 13/10/2026 with sixteen of them: sixty-four reads waiting on a database held by a writer delayed a health probe by
 * thirteen seconds, on a route that touches no database.</p>
 *
 * <p>A handler registered through this server is handed to a worker, and the virtual thread that received the request
 * parks until it is done - which releases its carrier. A request that waits on the database therefore occupies a
 * worker, never a carrier, and the number of workers is the number of requests that can be inside the database at
 * once, stated here and nowhere else. The whole handler runs on one worker, so the thread-confined connection scope
 * keeps its meaning.</p>
 *
 * <p>A request beyond the workers waits for one, in arrival order, for a bounded time; past it the request is refused
 * with {@code 429}, a named code and a {@code Retry-After}. A burst therefore queues instead of contending for the
 * database, and a queue that does not move does not grow without end.</p>
 *
 * <p>The routes that answer without the database are not registered through this server: they must stay answerable
 * when every worker is taken.</p>
 */
final class DatabaseWorkOffloadingHttpServer extends RequestProtectedHttpServer {
    static final int DATABASE_WORKERS = Math.max(2, Runtime.getRuntime().availableProcessors());

    /**
     * How long a request waits for a worker before it is refused: twice the five seconds a connection waits for a
     * busy database. A burst queues; a queue that does not move is told so, rather than growing without end.
     */
    static final Duration ADMISSION_WAIT = Duration.ofSeconds(10);
    static final int BUSY_STATUS = 429;
    static final String BUSY_CODE = "LOCAL_SERVER_BUSY";
    static final String RETRY_AFTER_SECONDS = "1";

    private static final byte[] BUSY_BODY = ("{\"apiVersion\":\"v1\",\"error\":{"
            + "\"code\":\"" + BUSY_CODE + "\","
            + "\"message\":\"every database worker of the local MORPHEUS API is taken and none was freed in time; "
            + "retry\","
            + "\"details\":{}}}").getBytes(StandardCharsets.UTF_8);

    private final ExecutorService workers;

    private DatabaseWorkOffloadingHttpServer(
            HttpServer delegate, ExecutorService workers, Semaphore admission, Duration admissionWait) {
        super(delegate, handler -> offloading(handler, workers, admission, admissionWait));
        this.workers = workers;
    }

    static DatabaseWorkOffloadingHttpServer over(HttpServer delegate) {
        return over(delegate, DATABASE_WORKERS, ADMISSION_WAIT);
    }

    static DatabaseWorkOffloadingHttpServer over(HttpServer delegate, int databaseWorkers, Duration admissionWait) {
        AtomicInteger number = new AtomicInteger();
        return new DatabaseWorkOffloadingHttpServer(
                delegate,
                Executors.newFixedThreadPool(
                        databaseWorkers,
                        work -> Thread.ofPlatform()
                                .daemon(true)
                                .name("morpheus-database-worker-" + number.incrementAndGet())
                                .unstarted(work)),
                // One permit per worker: a request holds a permit exactly while a worker handles it, so the pool's
                // own queue stays empty and the only queue is this one, fair and bounded in time.
                new Semaphore(databaseWorkers, true),
                admissionWait);
    }

    @Override
    void stopped() {
        workers.shutdownNow();
    }

    private static HttpHandler offloading(
            HttpHandler handler, ExecutorService workers, Semaphore admission, Duration admissionWait) {
        return exchange -> {
            if (!admitted(admission, admissionWait)) {
                refuseAsBusy(exchange);
                return;
            }
            OffloadedRequest request = new OffloadedRequest(handler, exchange, admission);
            try {
                workers.execute(request);
            } catch (RuntimeException refusedByThePool) {
                admission.release();
                throw refusedByThePool;
            }
            request.awaitOutcome();
        };
    }

    /**
     * One request on a worker. The place is given back by the worker, when it has finished with the request: a caller
     * that is interrupted leaves, but its worker is still taken until the handler returns, and a place returned by the
     * caller would admit a request no worker is free to handle.
     */
    private static final class OffloadedRequest implements Runnable {
        private final HttpHandler handler;
        private final HttpExchange exchange;
        private final Semaphore admission;
        private final CompletableFuture<Void> outcome = new CompletableFuture<>();
        private Thread worker;
        private boolean abandoned;

        private OffloadedRequest(HttpHandler handler, HttpExchange exchange, Semaphore admission) {
            this.handler = handler;
            this.exchange = exchange;
            this.admission = admission;
        }

        @Override
        public void run() {
            try {
                if (taken()) {
                    handler.handle(exchange);
                }
                outcome.complete(null);
            } catch (IOException | RuntimeException | Error raised) {
                outcome.completeExceptionally(raised);
            } finally {
                left();
                admission.release();
            }
        }

        /** Whether the caller is still waiting; a request abandoned before a worker took it is not handled. */
        private synchronized boolean taken() {
            worker = Thread.currentThread();
            return !abandoned;
        }

        /** Under the same lock as the interruption, so that it cannot land on this worker's next request. */
        private synchronized void left() {
            worker = null;
            Thread.interrupted();
        }

        private synchronized void abandon() {
            abandoned = true;
            if (worker != null) {
                worker.interrupt();
            }
        }

        private void awaitOutcome() throws IOException {
            try {
                outcome.get();
            } catch (InterruptedException interrupted) {
                abandon();
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while a database worker handled the request", interrupted);
            } catch (ExecutionException failed) {
                rethrow(failed.getCause());
            }
        }
    }

    private static boolean admitted(Semaphore admission, Duration admissionWait) throws IOException {
        try {
            return admission.tryAcquire(admissionWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for a database worker", interrupted);
        }
    }

    /** Refuses on the request's own thread: nothing was handed to a worker, and none is free to answer. */
    private static void refuseAsBusy(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Retry-After", RETRY_AFTER_SECONDS);
        MorpheusHttpResponseWriter.applySecurityHeaders(headers);
        exchange.sendResponseHeaders(BUSY_STATUS, BUSY_BODY.length);
        exchange.getResponseBody().write(BUSY_BODY);
        exchange.close();
    }

    /** A handler declares {@link IOException} and nothing else checked: what is neither that nor unchecked is an error. */
    private static void rethrow(Throwable cause) throws IOException {
        if (cause instanceof IOException io) {
            throw io;
        }
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw (Error) cause;
    }
}
