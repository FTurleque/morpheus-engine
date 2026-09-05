package com.morpheus.integration.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that a session factory failure does not leave the transport's workers running.
 *
 * <p>The transport allocates two single-thread executors in its field initializers, but only the path where both
 * workers finish disposes them -- and that path cannot run until {@code start()} has launched them. A factory
 * that throws in between left both threads alive for the life of the process, and left
 * {@code awaitTermination()} waiting on a latch nothing would count down.</p>
 */
class BoundedStdioServerTransportProviderFailedSessionTest {
    private static final String WORKER_PREFIX = "morpheus-mcp-server-";

    @Test
    void aFailingSessionFactoryLeavesNoTransportWorkerBehind() throws Exception {
        Set<Thread> before = workerThreads();

        BoundedStdioServerTransportProvider provider = provider();
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> provider.setSessionFactory(transport -> {
                    throw new IllegalStateException("session factory refused to build a session");
                }));
        assertEquals("session factory refused to build a session", failure.getMessage());

        assertTrue(
                provider.awaitTermination(Duration.ofSeconds(5)),
                "a provider that never produced a session must not leave awaitTermination blocked");

        assertNoWorkerThreadsLeaked(before);
    }

    /** An Error from the factory must release the workers just as a RuntimeException does. */
    @Test
    void anErrorFromTheSessionFactoryAlsoReleasesTheWorkers() throws Exception {
        Set<Thread> before = workerThreads();

        BoundedStdioServerTransportProvider provider = provider();
        assertThrows(ExceptionInInitializerError.class, () -> provider.setSessionFactory(transport -> {
            throw new ExceptionInInitializerError("tool registry failed to initialize");
        }));

        assertTrue(provider.awaitTermination(Duration.ofSeconds(5)));
        assertNoWorkerThreadsLeaked(before);
    }

    @Test
    void theSingleSessionContractStillRejectsASecondFactory() {
        BoundedStdioServerTransportProvider provider = provider();
        assertThrows(IllegalStateException.class, () -> provider.setSessionFactory(transport -> {
            throw new IllegalStateException("first attempt failed");
        }));

        IllegalStateException second = assertThrows(
                IllegalStateException.class,
                () -> provider.setSessionFactory(transport -> null));
        assertTrue(
                second.getMessage().contains("exactly one session"),
                () -> "expected the single-session contract to hold: " + second.getMessage());
    }

    private BoundedStdioServerTransportProvider provider() {
        return new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                64 * 1024,
                4);
    }

    /**
     * Waits on the threads themselves rather than sleeping: a disposed Reactor scheduler stops its executor
     * asynchronously, and Thread.join is the primitive that observes that, deterministically.
     */
    private static void assertNoWorkerThreadsLeaked(Set<Thread> before) throws InterruptedException {
        Set<Thread> candidates = workerThreads();
        candidates.removeAll(before);
        for (Thread worker : candidates) {
            worker.join(5_000);
        }

        Set<Thread> stillAlive = workerThreads();
        stillAlive.removeAll(before);
        assertTrue(
                stillAlive.isEmpty(),
                () -> "transport worker threads survived the failed session factory: "
                        + stillAlive.stream().map(Thread::getName).toList());
    }

    private static Set<Thread> workerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(thread -> thread.getName().startsWith(WORKER_PREFIX))
                .collect(Collectors.toCollection(java.util.HashSet::new));
    }

}
