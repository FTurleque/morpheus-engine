package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import org.junit.jupiter.api.Test;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lease may call itself closed only once its descriptor is actually gone.
 *
 * <p>It marked itself closed before either release ran and swallowed both {@link java.io.IOException}s, so a
 * failed {@code channel.close()} left a lease reporting itself released over a descriptor that may still be
 * open — and no retry could finish the job, because the next call returned immediately.</p>
 *
 * <p>Releasing the lock is best effort on its own: closing the channel releases the operating-system lock
 * anyway, so a failure there must never stop the call that actually frees the descriptor.</p>
 */
class SqliteServerLeaseCloseContractTest {

    @Test
    void aFailedLockReleaseStillClosesTheChannelAndCompletesTheLease() {
        AtomicInteger channelAttempts = new AtomicInteger();
        AtomicInteger lockAttempts = new AtomicInteger();
        FileChannel channel = FailingFileResources.channelFailingCloses(channelAttempts, 0);
        FileLock lock = FailingFileResources.lockFailingRelease(channel, lockAttempts);

        new SqliteServerMaintenance.ServerLease(channel, lock).close();

        assertEquals(1, lockAttempts.get(), "the lock release must be attempted");
        assertEquals(1, channelAttempts.get(), "a failed lock release must not stop the channel close");
    }

    @Test
    void aFailedChannelCloseLeavesTheLeaseRetryableRatherThanClosed() {
        AtomicInteger channelAttempts = new AtomicInteger();
        AtomicInteger lockAttempts = new AtomicInteger();
        FileChannel channel = FailingFileResources.channelFailingCloses(channelAttempts, 1);
        FileLock lock = FailingFileResources.lockReleasingNormally(channel, lockAttempts);
        SqliteServerMaintenance.ServerLease lease = new SqliteServerMaintenance.ServerLease(channel, lock);

        KnowledgeStoreException refused = assertThrows(KnowledgeStoreException.class, lease::close);
        assertEquals("Cannot release the MORPHEUS server lease", refused.getMessage());
        assertEquals(1, channelAttempts.get());

        // Retryable: the lease did not mark itself closed, so a second call runs the release again instead of
        // returning immediately. It is the lease that must stay open here -- the channel's own second close is
        // the JDK's no-op, which is exactly why the lease must not have claimed success on the first attempt.
        assertDoesNotThrow(lease::close, "a lease whose channel close failed must stay retryable");
        assertDoesNotThrow(lease::close, "a lease that completed is idempotent");
    }

    @Test
    void bothReleasesFailingStillAttemptsBothAndReportsThemTogether() {
        AtomicInteger channelAttempts = new AtomicInteger();
        AtomicInteger lockAttempts = new AtomicInteger();
        FileChannel channel = FailingFileResources.channelFailingCloses(channelAttempts, 1);
        FileLock lock = FailingFileResources.lockFailingRelease(channel, lockAttempts);
        SqliteServerMaintenance.ServerLease lease = new SqliteServerMaintenance.ServerLease(channel, lock);

        KnowledgeStoreException refused = assertThrows(KnowledgeStoreException.class, lease::close);

        assertEquals(1, lockAttempts.get());
        assertEquals(1, channelAttempts.get(), "the channel close must be attempted even when the lock failed");
        assertInstanceOf(java.io.IOException.class, refused.getCause());
        assertEquals(1, refused.getCause().getSuppressed().length,
                "the lock failure must be reported with the channel failure, not dropped");
        assertTrue(refused.getCause().getSuppressed()[0].getMessage().contains("lock release"));

        assertDoesNotThrow(lease::close, "the lease stays retryable after a double failure");
    }

    @Test
    void aNormalCloseReleasesBothExactlyOnceAndIsIdempotent() {
        AtomicInteger channelAttempts = new AtomicInteger();
        AtomicInteger lockAttempts = new AtomicInteger();
        FileChannel channel = FailingFileResources.channelFailingCloses(channelAttempts, 0);
        FileLock lock = FailingFileResources.lockReleasingNormally(channel, lockAttempts);
        SqliteServerMaintenance.ServerLease lease = new SqliteServerMaintenance.ServerLease(channel, lock);

        lease.close();
        lease.close();

        assertEquals(1, lockAttempts.get());
        assertEquals(1, channelAttempts.get());
    }
}
