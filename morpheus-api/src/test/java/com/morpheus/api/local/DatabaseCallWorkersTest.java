package com.morpheus.api.local;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseCallWorkersTest {
    private static final Duration GENEROUS = Duration.ofSeconds(30);

    private DatabaseCallWorkers workers;

    @AfterEach
    void close() {
        if (workers != null) {
            workers.close();
        }
    }

    @Test
    void aCallRunsOnANamedPlatformWorkerAndItsResultReachesItsCaller() throws Exception {
        workers = new DatabaseCallWorkers("test-database-worker-", 1, GENEROUS);

        Thread ranOn = workers.call(Thread::currentThread, GENEROUS);

        assertTrue(ranOn.getName().startsWith("test-database-worker-"), ranOn::getName);
        assertFalse(ranOn.isVirtual(), "a worker is a platform thread: it is the one allowed to wait");
    }

    /** Before 2026-11-03 a call beyond the workers waited in the pool's own queue, without end. */
    @Test
    void aCallBeyondTheWorkersIsRefusedByNameOnceTheAdmissionWaitHasPassed() throws Exception {
        workers = new DatabaseCallWorkers("test-database-worker-", 1, Duration.ofMillis(200));
        CountDownLatch holderInside = new CountDownLatch(1);
        CountDownLatch holderMayFinish = new CountDownLatch(1);
        Thread holder = Thread.ofPlatform().start(() -> {
            try {
                workers.runToItsEnd(() -> {
                    holderInside.countDown();
                    awaitQuietly(holderMayFinish);
                    return null;
                });
            } catch (Exception leftForTheAssertions) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(holderInside.await(10, TimeUnit.SECONDS));
        AtomicBoolean ran = new AtomicBoolean();

        DatabaseCallWorkers.RefusedCall refused = assertThrows(DatabaseCallWorkers.RefusedCall.class,
                () -> workers.call(() -> ran.getAndSet(true), GENEROUS));

        assertEquals(DatabaseCallWorkers.Refusal.NO_WORKER_IN_TIME, refused.refusal());
        assertEquals("every database worker stayed taken for 200 ms", refused.getMessage());
        assertFalse(ran.get(), "a call refused at admission is never handed to a worker");
        holderMayFinish.countDown();
        holder.join(10_000);
    }

    /** Before 2026-11-03 the caller waited for its worker without a deadline. */
    @Test
    void aCallStillRunningAtItsDeadlineIsRefusedByNameAndItsWorkerKeepsItsPlaceUntilItReturns() throws Exception {
        workers = new DatabaseCallWorkers("test-database-worker-", 1, Duration.ofMillis(200));
        CountDownLatch workerInterrupted = new CountDownLatch(1);
        CountDownLatch slowMayFinish = new CountDownLatch(1);
        AtomicReference<Thread> slowRanOn = new AtomicReference<>();

        DatabaseCallWorkers.RefusedCall refused = assertThrows(DatabaseCallWorkers.RefusedCall.class,
                () -> workers.call(() -> {
                    slowRanOn.set(Thread.currentThread());
                    boolean finished = false;
                    while (!finished) {
                        try {
                            finished = slowMayFinish.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            // Native SQLite does not stop when interrupted either: the worker stays taken.
                            workerInterrupted.countDown();
                        }
                    }
                    return null;
                }, Duration.ofMillis(200)));

        assertEquals(DatabaseCallWorkers.Refusal.DEADLINE_PASSED, refused.refusal());
        assertEquals("the database call did not finish within 200 ms", refused.getMessage());
        assertTrue(workerInterrupted.await(10, TimeUnit.SECONDS), "the worker is told its caller has left");
        DatabaseCallWorkers.RefusedCall noPlace = assertThrows(DatabaseCallWorkers.RefusedCall.class,
                () -> workers.call(() -> null, GENEROUS));
        assertEquals(DatabaseCallWorkers.Refusal.NO_WORKER_IN_TIME, noPlace.refusal(),
                "a caller that left does not give back the place its worker still holds");

        slowMayFinish.countDown();
        assertEquals(slowRanOn.get(), workers.call(Thread::currentThread, GENEROUS),
                "the place comes back once the late call has returned, on the same worker");
    }

    @Test
    void aDeadlineMustBePositive() {
        workers = new DatabaseCallWorkers("test-database-worker-", 1, GENEROUS);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> workers.call(() -> null, Duration.ZERO));

        assertEquals("deadline must be positive", refused.getMessage());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
