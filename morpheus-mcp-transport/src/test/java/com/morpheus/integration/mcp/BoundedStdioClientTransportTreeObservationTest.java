package com.morpheus.integration.mcp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A process of the peer whose descendants cannot be listed is reported to whoever launched the peer.
 *
 * <p>The observation used to drop that failure in an empty catch. The handles already retained are still terminated
 * at shutdown; what the failed listing would have added is not, and nothing said that an observation had been
 * incomplete.</p>
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

    private record StubProcess(long pid, List<ProcessHandle> below, RuntimeException listingFailure)
            implements ProcessHandle {
        static StubProcess leaf(long pid) {
            return new StubProcess(pid, List.of(), null);
        }

        static StubProcess parentOf(long pid, ProcessHandle child) {
            return new StubProcess(pid, List.of(child), null);
        }

        static StubProcess failing(long pid, RuntimeException listingFailure) {
            return new StubProcess(pid, List.of(), listingFailure);
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
            return true;
        }

        @Override
        public boolean destroyForcibly() {
            return true;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public int compareTo(ProcessHandle other) {
            return Long.compare(pid, other.pid());
        }
    }
}
