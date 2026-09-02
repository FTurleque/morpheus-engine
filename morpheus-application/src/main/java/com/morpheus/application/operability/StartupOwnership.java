package com.morpheus.application.operability;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Owns the resources created while a runtime is assembled, until assembly finishes.
 *
 * <p>Assembling a runtime acquires things the process cannot simply forget: a bound socket, an executor, an open
 * database. Assembly then continues, and anything after the first acquisition can fail. Without an explicit
 * owner, that failure leaves the socket bound and the threads alive, because the object that would have closed
 * them is exactly the object whose construction did not finish.</p>
 *
 * <p>Each acquisition is registered here as it happens. {@link #transferred()} is called once the finished
 * runtime holds everything and is responsible for closing it. Used with try-with-resources, a failure anywhere in
 * the body releases what was acquired, in reverse order, and the language attaches any release failure to the
 * original one as suppressed — so the reason assembly failed is never replaced by the reason cleanup failed.</p>
 *
 * <pre>{@code
 * try (StartupOwnership owned = new StartupOwnership()) {
 *     HttpServer server = owned.keep(HttpServer.create(address, 0), s -> s.stop(0));
 *     ExecutorService executor = owned.keep(Executors.newVirtualThreadPerTaskExecutor(), ExecutorService::shutdownNow);
 *     Runtime runtime = new Runtime(server, executor);
 *     owned.transferred();
 *     return runtime;
 * }
 * }</pre>
 *
 * <p>Forgetting {@link #transferred()} releases the resources on the successful path too, which fails loudly
 * rather than silently handing back a runtime whose socket has been closed underneath it.</p>
 */
public final class StartupOwnership implements AutoCloseable {
    private final Deque<Runnable> pending = new ArrayDeque<>();
    private boolean transferred;

    /** Registers one acquired resource with the action that releases it, and returns the resource. */
    public <T> T keep(T resource, Consumer<? super T> release) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(release, "release");
        pending.push(() -> release.accept(resource));
        return resource;
    }

    /** Registers a release action for something whose handle the caller keeps itself. */
    public void keepAction(Runnable release) {
        pending.push(Objects.requireNonNull(release, "release"));
    }

    /** Assembly succeeded: the assembled runtime owns everything registered here from now on. */
    public void transferred() {
        transferred = true;
    }

    @Override
    public void close() {
        if (transferred) {
            pending.clear();
            return;
        }
        IllegalStateException failures = null;
        while (!pending.isEmpty()) {
            try {
                pending.pop().run();
            } catch (RuntimeException | Error releaseFailure) {
                // Releasing the rest still matters, so collect instead of stopping at the first failure. These are
                // reported together, and as suppressed, because the caller needs to know why startup failed first.
                if (failures == null) {
                    failures = new IllegalStateException("cannot release a partially assembled MORPHEUS runtime");
                }
                failures.addSuppressed(releaseFailure);
            }
        }
        if (failures != null) {
            throw failures;
        }
    }
}
