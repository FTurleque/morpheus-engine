package com.morpheus.api;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * <p>The routes that answer without the database are not registered through this server: they must stay answerable
 * when every worker is taken.</p>
 */
final class DatabaseWorkOffloadingHttpServer extends RequestProtectedHttpServer {
    static final int DATABASE_WORKERS = Math.max(2, Runtime.getRuntime().availableProcessors());

    private final ExecutorService workers;

    private DatabaseWorkOffloadingHttpServer(HttpServer delegate, ExecutorService workers) {
        super(delegate, handler -> offloading(handler, workers));
        this.workers = workers;
    }

    static DatabaseWorkOffloadingHttpServer over(HttpServer delegate) {
        AtomicInteger number = new AtomicInteger();
        return new DatabaseWorkOffloadingHttpServer(delegate, Executors.newFixedThreadPool(
                DATABASE_WORKERS,
                work -> Thread.ofPlatform()
                        .daemon(true)
                        .name("morpheus-database-worker-" + number.incrementAndGet())
                        .unstarted(work)));
    }

    @Override
    void stopped() {
        workers.shutdownNow();
    }

    private static HttpHandler offloading(HttpHandler handler, ExecutorService workers) {
        return exchange -> {
            Future<Void> handled = workers.submit(() -> {
                handler.handle(exchange);
                return null;
            });
            try {
                handled.get();
            } catch (InterruptedException interrupted) {
                handled.cancel(true);
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while a database worker handled the request", interrupted);
            } catch (ExecutionException failed) {
                rethrow(failed.getCause());
            }
        };
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
