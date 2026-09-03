package com.morpheus.integration.mcp;

import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A transport owns one set of schedulers per configured peer, and disposes them when the peer goes away.
 *
 * <p>Disposed in sequence, the first failure ended the method and the rest kept their threads for the life of
 * the process. What must survive a refusing scheduler is the disposal of every other one.</p>
 */
class SchedulerReleaseTest {

    @Test
    void aSchedulerThatRefusesToStopDoesNotKeepTheOthersRunning() {
        List<String> disposed = new ArrayList<>();
        RuntimeException injected = new IllegalStateException("injected dispose failure");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> SchedulerRelease.disposeAll(
                recording(disposed, "inbound", null),
                recording(disposed, "outbound", injected),
                recording(disposed, "stderr", null),
                recording(disposed, "lifecycle", null)));

        assertSame(injected, thrown, "the first failure is what the caller sees");
        assertEquals(List.of("inbound", "stderr", "lifecycle"), disposed,
                "every scheduler after the refusing one must still have been disposed");
    }

    @Test
    void severalFailuresAreReportedTogetherWithoutSelfSuppression() {
        RuntimeException shared = new IllegalStateException("the same failure twice");
        List<String> disposed = new ArrayList<>();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> SchedulerRelease.disposeAll(
                recording(disposed, "inbound", shared),
                recording(disposed, "outbound", shared),
                recording(disposed, "stderr", null)));

        assertSame(shared, thrown);
        assertEquals(0, thrown.getSuppressed().length, "a throwable cannot be suppressed into itself");
        assertEquals(List.of("stderr"), disposed, "the release after the collision must still have run");
    }

    @Test
    void realSchedulersAreActuallyDisposed() {
        Scheduler inbound = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "test-inbound");
        Scheduler outbound = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "test-outbound");

        SchedulerRelease.disposeAll(inbound, outbound);

        assertTrue(inbound.isDisposed());
        assertTrue(outbound.isDisposed());
    }

    /** Records that it was asked to stop, and optionally refuses, so the order of disposal is observable. */
    private static Scheduler recording(List<String> disposed, String name, RuntimeException failure) {
        return new Scheduler() {
            @Override
            public reactor.core.Disposable schedule(Runnable task) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Worker createWorker() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void dispose() {
                if (failure != null) {
                    throw failure;
                }
                disposed.add(name);
            }
        };
    }
}
