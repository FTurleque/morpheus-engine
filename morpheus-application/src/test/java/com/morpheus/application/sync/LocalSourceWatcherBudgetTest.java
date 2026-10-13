package com.morpheus.application.sync;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import java.time.Duration;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalSourceWatcherBudgetTest {
    @TempDir
    Path tempDir;

    @Test
    void recursiveRegistrationFailsClosedWhenDirectoryBudgetIsExceeded() throws Exception {
        Files.createDirectories(tempDir.resolve("specs/a/b"));
        SourceScanPolicy policy = new SourceScanPolicy(
                Set.of(),
                8,
                2,
                100,
                1024,
                1024 * 100L);

        assertThrows(IOException.class,
                () -> new LocalSourceWatcher(tempDir, List.of(Path.of("specs")), policy));
    }

    /**
     * A directory created under a watched root that cannot be registered - here because the directory budget is spent -
     * is reported as an overflow, which asks for a rescan. A watcher that merely fell behind reports the same signal,
     * so the failure is counted: the new directory is left unwatched.
     */
    @Test
    void aNewDirectoryThatCannotBeWatchedIsReportedAsAnOverflowAndCounted() throws Exception {
        Files.createDirectories(tempDir.resolve("specs"));
        SourceScanPolicy onlyTheRoot = new SourceScanPolicy(Set.of(), 8, 1, 100, 1024, 1024 * 100L);
        OperationalMetrics metrics = new OperationalMetrics();
        try (LocalSourceWatcher watcher = new LocalSourceWatcher(tempDir, List.of(Path.of("specs")), onlyTheRoot,
                new OperationalRecorder(OperationalEventSink.noop(), metrics))) {
            Files.createDirectory(tempDir.resolve("specs/added"));

            List<SourceWatchSignal> signals = new ArrayList<>();
            for (int attempt = 0; attempt < 20 && !signals.contains(SourceWatchSignal.overflow()); attempt++) {
                signals.addAll(watcher.poll(Duration.ofSeconds(1)));
            }

            assertTrue(signals.contains(SourceWatchSignal.overflow()), () -> "signals: " + signals);
            assertEquals(1L, metrics.snapshot().counters().get("failure.suppressed.source-watch"));
        }
    }

    @Test
    void watchedRootBeyondDepthBudgetIsRejected() throws Exception {
        Files.createDirectories(tempDir.resolve("one/two/three"));
        SourceScanPolicy policy = new SourceScanPolicy(
                Set.of(),
                2,
                100,
                100,
                1024,
                1024 * 100L);

        assertThrows(IOException.class,
                () -> new LocalSourceWatcher(tempDir, List.of(Path.of("one/two/three")), policy));
    }
}
