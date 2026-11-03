package com.morpheus.api.local;

import com.morpheus.api.transport.MorpheusHttpResponseWriter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Delegating server whose handlers run on a bounded pool of platform threads while the request's own thread waits.
 *
 * <p>Every request of the local server runs on a virtual thread. The SQLite driver is native code, and it waits for a
 * busy database inside the native call, where a virtual thread stays mounted on its carrier - whatever the JDK, since
 * only the pinning of monitors was lifted after Java 21. The carriers are as many as the processors. Measured on
 * 13/10/2026 with sixteen of them: sixty-four reads waiting on a database held by a writer delayed a health probe by
 * thirteen seconds, on a route that touches no database.</p>
 *
 * <p>A handler registered through this server is handed whole to a {@link DatabaseCallWorkers} worker, and the virtual
 * thread that received the request parks until it is done - which releases its carrier. A request that waits on the
 * database therefore occupies a worker, never a carrier, and the number of workers is the number of requests that can
 * be inside the database at once ({@link DatabaseCallWorkers#DATABASE_WORKERS}).</p>
 *
 * <p>A request beyond the workers waits for one, in arrival order, for a bounded time; past it the request is refused
 * with {@code 429}, a named code and a {@code Retry-After}, written on the request's own thread. A burst therefore
 * queues instead of contending for the database, and a queue that does not move does not grow without end.</p>
 *
 * <p>The routes that answer without the database are not registered through this server: they must stay answerable
 * when every worker is taken.</p>
 */
public final class DatabaseWorkOffloadingHttpServer extends RequestProtectedHttpServer {
    static final int BUSY_STATUS = 429;
    static final String BUSY_CODE = "LOCAL_SERVER_BUSY";
    static final String RETRY_AFTER_SECONDS = "1";

    private static final byte[] BUSY_BODY = ("{\"apiVersion\":\"v1\",\"error\":{"
            + "\"code\":\"" + BUSY_CODE + "\","
            + "\"message\":\"every database worker of the local MORPHEUS API is taken and none was freed in time; "
            + "retry\","
            + "\"details\":{}}}").getBytes(StandardCharsets.UTF_8);

    private final DatabaseCallWorkers workers;

    private DatabaseWorkOffloadingHttpServer(HttpServer delegate, DatabaseCallWorkers workers) {
        super(delegate, handler -> offloading(handler, workers));
        this.workers = workers;
    }

    static DatabaseWorkOffloadingHttpServer over(HttpServer delegate) {
        return over(delegate, DatabaseCallWorkers.DATABASE_WORKERS, DatabaseCallWorkers.ADMISSION_WAIT);
    }

    static DatabaseWorkOffloadingHttpServer over(HttpServer delegate, int databaseWorkers, Duration admissionWait) {
        return new DatabaseWorkOffloadingHttpServer(
                delegate, new DatabaseCallWorkers("morpheus-database-worker-", databaseWorkers, admissionWait));
    }

    @Override
    void stopped() {
        workers.close();
    }

    private static HttpHandler offloading(HttpHandler handler, DatabaseCallWorkers workers) {
        return exchange -> {
            try {
                workers.runToItsEnd(new HandlerOnAWorker(handler, exchange));
            } catch (DatabaseCallWorkers.RefusedCall noWorkerInTime) {
                refuseAsBusy(exchange);
            }
        };
    }

    /** One request, handled whole on a worker: the handler writes its own response there. */
    private record HandlerOnAWorker(HttpHandler handler, HttpExchange exchange)
            implements DatabaseCallWorkers.DatabaseCall<Void> {
        @Override
        public Void run() throws IOException {
            handler.handle(exchange);
            return null;
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
}
