package com.morpheus.api;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Runs a call that waits on the database on a bounded pool of platform threads while the caller's virtual thread parks.
 *
 * <p>The SQLite driver waits for a busy database inside native code, where a virtual thread stays mounted on its
 * carrier, and there are as many carriers as processors (ADR-0114). A route the remote facade serves with SQLite in its
 * own hands would otherwise hold one carrier per request in flight; here it holds a worker, and the caller releases its
 * carrier by waiting on a future. The whole call runs on one worker, so the thread-confined connection scope keeps its
 * meaning. Requests that reach it are already bounded by the facade's privileged lane, so the pool has no admission of
 * its own.</p>
 */
public final class DatabaseCallWorkers implements AutoCloseable {
    private final ExecutorService workers;

    public DatabaseCallWorkers(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        AtomicInteger number = new AtomicInteger();
        this.workers = Executors.newFixedThreadPool(
                count,
                work -> Thread.ofPlatform()
                        .daemon(true)
                        .name("morpheus-remote-database-worker-" + number.incrementAndGet())
                        .unstarted(work));
    }

    public <T> T call(Supplier<T> call) {
        Objects.requireNonNull(call, "call");
        Future<T> outcome = workers.submit(call::get);
        try {
            return outcome.get();
        } catch (InterruptedException interrupted) {
            outcome.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while a database worker ran the call", interrupted);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw (Error) cause;
        }
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }
}
