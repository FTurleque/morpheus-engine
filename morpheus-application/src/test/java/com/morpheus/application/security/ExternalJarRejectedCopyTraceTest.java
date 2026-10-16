package com.morpheus.application.security;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A copy that failed its integrity check and could not be deleted is counted.
 *
 * <p>The caller is told about the integrity failure, which is the one that matters to it. The copy left in the
 * temporary directory was reported nowhere.</p>
 */
class ExternalJarRejectedCopyTraceTest {
    @TempDir
    Path temporaryDirectory;

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);

    @Test
    void aRejectedCopyThatIsDeletedLeavesNothingAndOneThatCannotBeIsCounted() throws IOException {
        Path deletable = Files.writeString(temporaryDirectory.resolve("copy.jar"), "rejected");
        Path undeletable = Files.createDirectory(temporaryDirectory.resolve("occupied"));
        Files.writeString(undeletable.resolve("child"), "a directory that is not empty cannot be deleted");

        ExternalJarIntegrity.deleteRejectedCopy(deletable, recorder);
        assertFalse(Files.exists(deletable));
        assertEquals(Map.of(), metrics.snapshot().counters());

        ExternalJarIntegrity.deleteRejectedCopy(undeletable, recorder);

        assertTrue(Files.exists(undeletable));
        assertEquals(Map.of("failure.suppressed.staged-artifact-cleanup", 1L), metrics.snapshot().counters());
    }
}
