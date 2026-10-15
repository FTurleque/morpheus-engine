package com.morpheus.sdk.provider;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the clean-up of a plugin probe sets aside is counted.
 *
 * <p>The parent ends every probe by terminating the processes it observed and deleting the directory the result came
 * back through. Three of those steps held a catch that said nothing: a process whose descendants could not be listed,
 * a process that could not be signalled, a directory that could not be deleted. None changes the outcome of the probe,
 * and each can leave something behind - a process, or a residue on disk.</p>
 */
class ProviderPluginProbeCleanupTraceTest {
    @TempDir
    Path temporaryDirectory;

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final ProviderPluginProbeProcess probe = new ProviderPluginProbeProcess(
            Duration.ofSeconds(1), new OperationalRecorder(OperationalEventSink.noop(), metrics));

    @Test
    void aStagingDirectoryThatCannotBeDeletedIsCountedAndOneThatCanIsNot() throws Exception {
        Path deletable = Files.createDirectory(temporaryDirectory.resolve("deletable"));
        Files.writeString(deletable.resolve(ProviderPluginProbeProcess.RESULT_FILE_NAME), "result");
        Path occupied = Files.createDirectory(temporaryDirectory.resolve("occupied"));
        Files.writeString(occupied.resolve("left-by-the-plugin"), "a directory that is not empty cannot be deleted");

        probe.deleteStagingDirectory(null);
        probe.deleteStagingDirectory(deletable);
        assertFalse(Files.exists(deletable));
        assertEquals(Map.of(), metrics.snapshot().counters());

        probe.deleteStagingDirectory(occupied);

        assertTrue(Files.exists(occupied));
        assertEquals(Map.of("failure.suppressed.staged-artifact-cleanup", 1L), metrics.snapshot().counters());
    }

    @Test
    void aProcessWhoseDescendantsCannotBeListedIsCountedAndTheOthersAreStillRetained() {
        StubProcess child = StubProcess.healthy(201L, List.of());
        StubProcess healthy = StubProcess.healthy(102L, List.of(child));
        StubProcess broken = StubProcess.unlistable(101L);
        StubProcess root = StubProcess.healthy(100L, List.of(broken, healthy));
        Map<Long, ProcessHandle> observed = new LinkedHashMap<>();

        probe.collectTree(root, observed);
        assertEquals(Set.of(100L, 101L, 102L), observed.keySet());
        assertEquals(Map.of(), metrics.snapshot().counters());

        probe.collectTree(root, observed);

        assertEquals(Set.of(100L, 101L, 102L, 201L), observed.keySet());
        assertEquals(Map.of("failure.suppressed.plugin-probe-process-tree", 1L), metrics.snapshot().counters());
    }

    @Test
    void aProcessThatCannotBeSignalledIsCountedOnEachPassAndADeadOneIsLeftAlone() {
        StubProcess unsignallable = StubProcess.unsignallable(101L);
        StubProcess dead = StubProcess.dead(102L);
        StubProcess healthy = StubProcess.healthy(103L, List.of());

        probe.signal(dead, false);
        probe.signal(healthy, false);
        probe.signal(healthy, true);
        assertEquals(Map.of(), metrics.snapshot().counters());

        probe.signal(unsignallable, false);
        probe.signal(unsignallable, true);

        assertEquals(Map.of("failure.suppressed.plugin-probe-shutdown", 2L), metrics.snapshot().counters());
    }

    private record StubProcess(long pid, boolean alive, List<ProcessHandle> below, boolean listable, boolean signallable)
            implements ProcessHandle {
        static StubProcess healthy(long pid, List<ProcessHandle> below) {
            return new StubProcess(pid, true, below, true, true);
        }

        static StubProcess unlistable(long pid) {
            return new StubProcess(pid, true, List.of(), false, true);
        }

        static StubProcess unsignallable(long pid) {
            return new StubProcess(pid, true, List.of(), true, false);
        }

        static StubProcess dead(long pid) {
            return new StubProcess(pid, false, List.of(), false, false);
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            if (!listable) {
                throw new IllegalStateException("the process table cannot be read");
            }
            return below.stream();
        }

        @Override
        public Stream<ProcessHandle> children() {
            return descendants();
        }

        @Override
        public boolean destroy() {
            return signalled();
        }

        @Override
        public boolean destroyForcibly() {
            return signalled();
        }

        private boolean signalled() {
            if (!signallable) {
                throw new IllegalStateException("the process cannot be signalled");
            }
            return true;
        }

        @Override
        public Optional<ProcessHandle> parent() {
            return Optional.empty();
        }

        @Override
        public Info info() {
            throw new UnsupportedOperationException("not read by the clean-up");
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
        public int compareTo(ProcessHandle other) {
            return Long.compare(pid, other.pid());
        }
    }
}
