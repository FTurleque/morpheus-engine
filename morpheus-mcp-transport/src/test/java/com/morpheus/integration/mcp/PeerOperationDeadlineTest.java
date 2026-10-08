package com.morpheus.integration.mcp;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeerOperationDeadlineTest {

    @Test
    void anOperationStillRunningAtItsDeadlineIsAbortedOnce() throws Exception {
        CountDownLatch aborted = new CountDownLatch(1);
        AtomicInteger aborts = new AtomicInteger();

        try (PeerOperationDeadline deadline = PeerOperationDeadline.arm(Duration.ofMillis(50), () -> {
            aborts.incrementAndGet();
            aborted.countDown();
        })) {
            assertTrue(aborted.await(10, TimeUnit.SECONDS));
            assertTrue(deadline.expired());
        }
        assertEquals(1, aborts.get());
    }

    @Test
    void anOperationThatEndsInTimeIsNeverAborted() throws Exception {
        AtomicInteger aborts = new AtomicInteger();

        PeerOperationDeadline deadline = PeerOperationDeadline.arm(Duration.ofMillis(200), aborts::incrementAndGet);
        deadline.close();
        Thread.sleep(400);

        assertFalse(deadline.expired());
        assertEquals(0, aborts.get());
    }

    @Test
    void anAbortThatFailsStillLeavesTheDeadlineExpired() throws Exception {
        CountDownLatch attempted = new CountDownLatch(1);

        try (PeerOperationDeadline deadline = PeerOperationDeadline.arm(Duration.ofMillis(20), () -> {
            attempted.countDown();
            throw new IllegalStateException("client already closed");
        })) {
            assertTrue(attempted.await(10, TimeUnit.SECONDS));
            assertTrue(deadline.expired());
        }
    }

    @Test
    void theOperationDeadlineIsEveryRequestInSequencePlusTheStartUpAllowance() {
        assertEquals(Duration.ofSeconds(3 * 20 + 15), PeerOperationDeadline.of(Duration.ofSeconds(20), 3));
        assertEquals(Duration.ofSeconds(4 * 120 + 15), PeerOperationDeadline.LONGEST_OPERATION);
    }

    @Test
    void anOperationOutsideTheEnvelopeIsRefused() {
        IllegalArgumentException tooLong = assertThrows(IllegalArgumentException.class,
                () -> PeerOperationDeadline.of(Duration.ofSeconds(121), 1));
        assertTrue(tooLong.getMessage().contains("at most 120 s"), tooLong.getMessage());
        IllegalArgumentException tooMany = assertThrows(IllegalArgumentException.class,
                () -> PeerOperationDeadline.of(Duration.ofSeconds(1), 5));
        assertTrue(tooMany.getMessage().contains("between 1 and 4"), tooMany.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PeerOperationDeadline.of(Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> PeerOperationDeadline.arm(Duration.ZERO, () -> { }));
    }
}
