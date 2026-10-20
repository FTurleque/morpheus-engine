package com.morpheus.api;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A handler registered through the database workers runs on one of them, and what it raises comes back to the request.
 *
 * <p>The decorator is what keeps a wait inside the SQLite driver off the carriers of the virtual-thread scheduler. It
 * must not change what a handler's caller sees: the same failure, on the thread that received the request.</p>
 */
class DatabaseWorkOffloadingHttpServerTest {
    private DatabaseWorkOffloadingHttpServer server;

    @BeforeEach
    void create() throws IOException {
        server = DatabaseWorkOffloadingHttpServer.over(HttpServer.create());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aHandlerRunsOnADatabaseWorkerWhileItsCallerWaits() throws IOException {
        AtomicReference<Thread> ranOn = new AtomicReference<>();

        offloaded(exchange -> ranOn.set(Thread.currentThread())).handle(null);

        assertNotSame(Thread.currentThread(), ranOn.get());
        assertTrue(ranOn.get().getName().startsWith("morpheus-database-worker-"), ranOn.get()::getName);
        assertTrue(ranOn.get().isDaemon());
        assertTrue(!ranOn.get().isVirtual(), "a worker is a platform thread: it is the one allowed to wait");
    }

    @Test
    void whatAHandlerRaisesReachesItsCallerUnchanged() {
        IOException io = new IOException("the response could not be written");
        IllegalStateException runtime = new IllegalStateException("the store refused");
        Error error = new StackOverflowError("a defect");

        assertSame(io, assertThrows(IOException.class, () -> offloaded(exchange -> {
            throw io;
        }).handle(null)));
        assertSame(runtime, assertThrows(IllegalStateException.class, () -> offloaded(exchange -> {
            throw runtime;
        }).handle(null)));
        assertSame(error, assertThrows(StackOverflowError.class, () -> offloaded(exchange -> {
            throw error;
        }).handle(null)));
    }

    @Test
    void aCallerInterruptedWhileItWaitsGivesUpAndInterruptsItsWorker() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch workerInterrupted = new CountDownLatch(1);
        AtomicBoolean callerStillInterrupted = new AtomicBoolean();
        AtomicReference<Throwable> raised = new AtomicReference<>();
        HttpHandler waiting = offloaded(exchange -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException interrupted) {
                workerInterrupted.countDown();
            }
        });
        Thread caller = Thread.ofPlatform().start(() -> {
            try {
                waiting.handle(null);
            } catch (IOException | RuntimeException failure) {
                raised.set(failure);
            }
            callerStillInterrupted.set(Thread.currentThread().isInterrupted());
        });
        assertTrue(started.await(10, TimeUnit.SECONDS));

        caller.interrupt();
        caller.join(10_000);

        assertEquals(IOException.class, raised.get().getClass());
        assertEquals("interrupted while a database worker handled the request", raised.get().getMessage());
        assertTrue(callerStillInterrupted.get(), "the interruption is not swallowed");
        assertTrue(workerInterrupted.await(10, TimeUnit.SECONDS), "the worker is not left waiting for nobody");
    }

    /**
     * The place belongs to the worker, not to the caller: a caller that is interrupted leaves at once, but its worker
     * is taken until the handler returns, and a place given back by the caller would admit a request that no worker
     * is free to handle.
     */
    @Test
    void aCallerThatLeavesDoesNotGiveBackThePlaceItsWorkerStillHolds() throws Exception {
        server.stop(0);
        server = DatabaseWorkOffloadingHttpServer.over(HttpServer.create(), 1, Duration.ofSeconds(30));
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch firstMayFinish = new CountDownLatch(1);
        CountDownLatch secondInside = new CountDownLatch(1);
        HttpHandler deafToInterruption = offloaded(exchange -> {
            firstInside.countDown();
            boolean finished = false;
            while (!finished) {
                try {
                    finished = firstMayFinish.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException ignoredOnPurpose) {
                    // The handler under test does not stop when interrupted: the worker stays taken.
                }
            }
        });
        HttpHandler second = offloaded(exchange -> secondInside.countDown());
        Thread firstCaller = Thread.ofPlatform().start(() -> runQuietly(deafToInterruption));
        assertTrue(firstInside.await(10, TimeUnit.SECONDS));

        firstCaller.interrupt();
        firstCaller.join(10_000);
        Thread secondCaller = Thread.ofPlatform().start(() -> runQuietly(second));
        BoundedWait.until(
                "the second caller to be waiting for a place",
                Duration.ofSeconds(10),
                BoundedWait.LOCAL_COUNTER_POLL,
                () -> secondCaller.getState() == Thread.State.TIMED_WAITING,
                () -> "second caller is " + secondCaller.getState());
        assertEquals(1, secondInside.getCount(), "the only worker is still handling the first request");

        firstMayFinish.countDown();

        assertTrue(secondInside.await(10, TimeUnit.SECONDS), "the place is given once the worker is free");
        secondCaller.join(10_000);
    }

    @Test
    void aCallerInterruptedWhileItWaitsForAPlaceGivesUpWithoutTakingOne() throws Exception {
        server.stop(0);
        server = DatabaseWorkOffloadingHttpServer.over(HttpServer.create(), 1, Duration.ofSeconds(30));
        CountDownLatch holderInside = new CountDownLatch(1);
        CountDownLatch holderMayFinish = new CountDownLatch(1);
        AtomicReference<Throwable> raised = new AtomicReference<>();
        AtomicBoolean handled = new AtomicBoolean();
        HttpHandler holding = offloaded(exchange -> {
            holderInside.countDown();
            try {
                holderMayFinish.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        HttpHandler waitingForAPlace = offloaded(exchange -> handled.set(true));
        Thread holder = Thread.ofPlatform().start(() -> runQuietly(holding));
        assertTrue(holderInside.await(10, TimeUnit.SECONDS));
        Thread waiter = Thread.ofPlatform().start(() -> {
            try {
                waitingForAPlace.handle(null);
            } catch (IOException | RuntimeException failure) {
                raised.set(failure);
            }
        });
        BoundedWait.until(
                "the second caller to be waiting for a place",
                Duration.ofSeconds(10),
                BoundedWait.LOCAL_COUNTER_POLL,
                () -> waiter.getState() == Thread.State.TIMED_WAITING,
                () -> "second caller is " + waiter.getState());

        waiter.interrupt();
        waiter.join(10_000);
        holderMayFinish.countDown();
        holder.join(10_000);

        assertEquals("interrupted while waiting for a database worker", raised.get().getMessage());
        assertEquals(IOException.class, raised.get().getClass());
        assertTrue(!handled.get(), "a request that never had a place is not handled");
        assertTrue(offloadedRunsNow(), "the place the holder had is free again, and no other was taken");
    }

    private boolean offloadedRunsNow() throws IOException {
        AtomicBoolean ran = new AtomicBoolean();
        offloaded(exchange -> ran.set(true)).handle(null);
        return ran.get();
    }

    private static void runQuietly(HttpHandler handler) {
        try {
            handler.handle(null);
        } catch (IOException | RuntimeException leftForTheAssertions) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void theWorkersStopWithTheServerTheyBelongTo() {
        HttpHandler handler = offloaded(exchange -> { });

        server.stop(0);

        RejectedExecutionException failure =
                assertThrows(RejectedExecutionException.class, () -> handler.handle(null));
        assertTrue(failure.getMessage().contains("rejected from") && failure.getMessage().contains("Terminated"),
                () -> "the pool of a stopped server must be the one that refused: " + failure.getMessage());
    }

    private HttpHandler offloaded(HttpHandler handler) {
        return server.createContext("/work-" + System.nanoTime(), handler).getHandler();
    }
}
