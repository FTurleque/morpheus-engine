package com.morpheus.integration.mcp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A process of the peer whose descendants cannot be listed, or that cannot be signalled, is reported to whoever
 * launched the peer.
 *
 * <p>The observation used to drop that failure in an empty catch. The handles already retained are still terminated
 * at shutdown; what the failed listing would have added is not, and nothing said that an observation had been
 * incomplete.</p>
 *
 * <p>A signal that failed - on the root of the peer or on a retained descendant - was logged at {@code DEBUG}, which
 * the default handler of the JDK drops: a process that may outlive the peer left no trace either.</p>
 */
class BoundedStdioClientTransportTreeObservationTest {
    @Test
    void aHandleWhoseDescendantsCannotBeListedIsReportedAndTheOthersAreStillRead() {
        IllegalStateException unlisted = new IllegalStateException("the process table cannot be read");
        StubProcess child = StubProcess.leaf(201L);
        StubProcess broken = StubProcess.failing(101L, unlisted);
        StubProcess healthy = StubProcess.parentOf(102L, child);
        Map<Long, ProcessHandle> observed = new ConcurrentHashMap<>(Map.of(101L, broken, 102L, healthy));
        List<RuntimeException> reported = new ArrayList<>();

        BoundedStdioClientTransport.retainDescendants(List.of(broken, healthy), observed, reported::add);

        assertEquals(1, reported.size());
        assertSame(unlisted, reported.get(0));
        assertEquals(Set.of(101L, 102L, 201L), observed.keySet());
    }

    @Test
    void aTreeThatIsListedReportsNothing() {
        StubProcess child = StubProcess.leaf(201L);
        StubProcess root = StubProcess.parentOf(100L, child);
        Map<Long, ProcessHandle> observed = new ConcurrentHashMap<>(Map.of(100L, root));
        List<RuntimeException> reported = new ArrayList<>();

        BoundedStdioClientTransport.retainDescendants(List.of(root), observed, reported::add);

        assertEquals(List.of(), reported);
        assertEquals(Set.of(100L, 201L), observed.keySet());
    }

    @Test
    void aProcessThatRefusesItsSignalIsReportedAndTheOthersAreStillSignalled() {
        IllegalStateException refused = new IllegalStateException("the process cannot be signalled");
        StubProcess stubborn = StubProcess.refusing(101L, refused);
        StubProcess obedient = StubProcess.leaf(102L);
        List<RuntimeException> reported = new ArrayList<>();

        BoundedStdioClientTransport.terminate(List.of(stubborn, obedient), true, reported::add);

        assertEquals(1, reported.size());
        assertSame(refused, reported.get(0));
        assertEquals(1, obedient.forced().get(), "a refusal must not end the pass before the next process");
        assertEquals(0, obedient.graceful().get());
    }

    @Test
    void aGracefulSignalThatIsRefusedIsReportedToo() {
        IllegalStateException refused = new IllegalStateException("the process cannot be signalled");
        StubProcess stubborn = StubProcess.refusing(101L, refused);
        List<RuntimeException> reported = new ArrayList<>();

        BoundedStdioClientTransport.terminate(List.of(stubborn), false, reported::add);

        assertEquals(List.of(refused), reported);
    }

    @Test
    void aProcessAlreadyDeadIsNotSignalledAndATreeThatTerminatesReportsNothing() {
        StubProcess dead = StubProcess.dead(101L);
        StubProcess live = StubProcess.leaf(102L);
        List<RuntimeException> reported = new ArrayList<>();

        BoundedStdioClientTransport.terminate(List.of(dead, live), false, reported::add);

        assertEquals(List.of(), reported);
        assertEquals(0, dead.graceful().get() + dead.forced().get());
        assertEquals(1, live.graceful().get());
        assertEquals(0, live.forced().get());
    }

    private record StubProcess(
            long pid,
            List<ProcessHandle> below,
            RuntimeException listingFailure,
            RuntimeException signalFailure,
            boolean alive,
            AtomicInteger graceful,
            AtomicInteger forced) implements ProcessHandle {
        static StubProcess leaf(long pid) {
            return new StubProcess(pid, List.of(), null, null, true, new AtomicInteger(), new AtomicInteger());
        }

        static StubProcess parentOf(long pid, ProcessHandle child) {
            return new StubProcess(pid, List.of(child), null, null, true, new AtomicInteger(), new AtomicInteger());
        }

        static StubProcess failing(long pid, RuntimeException listingFailure) {
            return new StubProcess(pid, List.of(), listingFailure, null, true, new AtomicInteger(), new AtomicInteger());
        }

        static StubProcess refusing(long pid, RuntimeException signalFailure) {
            return new StubProcess(pid, List.of(), null, signalFailure, true, new AtomicInteger(), new AtomicInteger());
        }

        static StubProcess dead(long pid) {
            return new StubProcess(pid, List.of(), null, null, false, new AtomicInteger(), new AtomicInteger());
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            if (listingFailure != null) {
                throw listingFailure;
            }
            return below.stream();
        }

        @Override
        public Stream<ProcessHandle> children() {
            return descendants();
        }

        @Override
        public Optional<ProcessHandle> parent() {
            return Optional.empty();
        }

        @Override
        public Info info() {
            throw new UnsupportedOperationException("not read by the observation");
        }

        @Override
        public CompletableFuture<ProcessHandle> onExit() {
            return new CompletableFuture<>();
        }

        @Override
        public boolean supportsNormalTermination() {
            return true;
        }

        @Override
        public boolean destroy() {
            if (signalFailure != null) {
                throw signalFailure;
            }
            graceful.incrementAndGet();
            return true;
        }

        @Override
        public boolean destroyForcibly() {
            if (signalFailure != null) {
                throw signalFailure;
            }
            forced.incrementAndGet();
            return true;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public int compareTo(ProcessHandle other) {
            return Long.compare(pid, other.pid());
        }
    }
}
