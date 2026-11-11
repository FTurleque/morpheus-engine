package com.morpheus.store.sqlite.runtime;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A lease that could not be given back cleanly says so.
 *
 * <p>The release of a lock and the close of its channel each held an empty catch. A channel that does not close keeps
 * the operating-system lock while the lease is reported as returned: the next open of that database is refused as
 * busy, and nothing told the operator why.</p>
 */
class SqliteLeaseReleaseTraceTest {
    @TempDir
    Path temporaryDirectory;

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);

    @Test
    void aLeaseGivenBackCleanlyIsNotCounted() throws IOException {
        FileChannel channel = FileChannel.open(temporaryDirectory.resolve("lease.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = channel.lock();

        SqliteDatabaseLease.releaseCounting(lock, channel, recorder);

        assertFalse(lock.isValid());
        assertFalse(channel.isOpen());
        assertEquals(Map.of(), metrics.snapshot().counters());
    }

    @Test
    void aLockThatCannotBeReleasedIsCountedAndItsChannelIsStillClosed() throws IOException {
        AtomicInteger closed = new AtomicInteger();
        try (FileChannel channel = FileChannel.open(temporaryDirectory.resolve("lease.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock unreleasable = new FileLock(channel, 0L, 1L, false) {
                @Override
                public boolean isValid() {
                    return true;
                }

                @Override
                public void release() throws IOException {
                    throw new IOException("the lock cannot be released");
                }
            };

            SqliteDatabaseLease.releaseCounting(unreleasable, closed::incrementAndGet, recorder);
        }

        assertEquals(1, closed.get());
        assertEquals(Map.of("failure.suppressed.sqlite-lease-release", 1L), metrics.snapshot().counters());
    }

    @Test
    void aChannelThatCannotBeClosedIsCounted() {
        Closeable unclosable = () -> {
            throw new IOException("the channel cannot be closed");
        };

        SqliteDatabaseLease.closeCounting(unclosable, recorder);

        assertEquals(Map.of("failure.suppressed.sqlite-lease-release", 1L), metrics.snapshot().counters());
    }
}
