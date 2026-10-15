package com.morpheus.integration.nexus;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the NEXUS integration sets aside is counted.
 *
 * <p>A peer that could not be aborted, and a verified copy of its JAR that could not be deleted, used to leave
 * nothing: the catch held a comment. Neither can be repaired where it happens; both are now counted, so that a peer
 * left running or a residue on disk is not discovered by accident.</p>
 */
class NexusSuppressedFailureTraceTest {
    @TempDir
    Path temporaryDirectory;

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);

    @Test
    void aVerifiedCopyThatCannotBeDeletedIsCountedAndOneThatCanIsNot() throws Exception {
        Path deletable = Files.writeString(temporaryDirectory.resolve("copy"), "jar");
        Path undeletable = Files.createDirectory(temporaryDirectory.resolve("occupied"));
        Files.writeString(undeletable.resolve("child"), "a directory that is not empty cannot be deleted");

        NexusMcpContextGateway.deleteStagedCounting(Optional.of(deletable), recorder);
        NexusMcpContextGateway.deleteStagedCounting(Optional.empty(), recorder);
        assertFalse(Files.exists(deletable));
        assertEquals(Map.of(), metrics.snapshot().counters());

        NexusMcpContextGateway.deleteStagedCounting(Optional.of(undeletable), recorder);

        assertTrue(Files.exists(undeletable));
        assertEquals(Map.of("failure.suppressed.staged-artifact-cleanup", 1L), metrics.snapshot().counters());
    }

    /**
     * The transport may depend on no other MORPHEUS package: it reports a process of the peer it could not list or
     * signal to the observer the gateway builds it with, and that observer is what counts. A process left out of the
     * tree, or one that refused its signal, may outlive the peer.
     */
    @Test
    void aProcessOfThePeerThatCannotBeListedOrSignalledIsCountedOncePerFailure() {
        Consumer<Throwable> processTreeFailures = NexusMcpContextGateway.processTreeFailures(recorder);
        assertEquals(Map.of(), metrics.snapshot().counters());

        processTreeFailures.accept(new IllegalStateException("the process table cannot be read"));
        processTreeFailures.accept(new UnsupportedOperationException("the process cannot be signalled"));

        assertEquals(Map.of("failure.suppressed.mcp-peer-process-tree", 2L), metrics.snapshot().counters());
    }

    @Test
    void anAbortThatFailsIsCountedAndStillFailsForTheDeadlineThatAskedForIt() {
        AtomicInteger aborted = new AtomicInteger();
        Runnable healthy = NexusMcpContextGateway.countingAbort(aborted::incrementAndGet, recorder);
        Runnable broken = NexusMcpContextGateway.countingAbort(() -> {
            throw new IllegalStateException("the peer could not be closed");
        }, recorder);

        healthy.run();
        assertEquals(1, aborted.get());
        assertEquals(Map.of(), metrics.snapshot().counters());

        IllegalStateException failure = assertThrows(IllegalStateException.class, broken::run);

        assertEquals("the peer could not be closed", failure.getMessage());
        assertEquals(Map.of("failure.suppressed.mcp-peer-shutdown", 1L), metrics.snapshot().counters());
    }
}
