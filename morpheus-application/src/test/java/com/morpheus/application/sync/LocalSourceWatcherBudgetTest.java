package com.morpheus.application.sync;

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
                false,
                8,
                2,
                100,
                1024,
                1024 * 100L);

        assertThrows(IOException.class,
                () -> new LocalSourceWatcher(tempDir, List.of(Path.of("specs")), policy));
    }

    @Test
    void watchedRootBeyondDepthBudgetIsRejected() throws Exception {
        Files.createDirectories(tempDir.resolve("one/two/three"));
        SourceScanPolicy policy = new SourceScanPolicy(
                Set.of(),
                false,
                2,
                100,
                100,
                1024,
                1024 * 100L);

        assertThrows(IOException.class,
                () -> new LocalSourceWatcher(tempDir, List.of(Path.of("one/two/three")), policy));
    }
}
