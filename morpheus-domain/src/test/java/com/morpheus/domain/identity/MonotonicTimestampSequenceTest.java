package com.morpheus.domain.identity;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonotonicTimestampSequenceTest {

    private static final int BITS = MonotonicTimestampSequence.COUNTER_BITS;

    @Test
    void aNewMillisecondTakesTheSeedWithItsLeftmostBitCleared() {
        MonotonicTimestampSequence sequence = new MonotonicTimestampSequence(seeds(0xFFF, 5));

        assertEquals((1_000L << BITS) | 0x7FF, sequence.next(1_000L));
        assertEquals((1_001L << BITS) | 5, sequence.next(1_001L));
    }

    @Test
    void theSameMillisecondIncrementsTheCounter() {
        MonotonicTimestampSequence sequence = new MonotonicTimestampSequence(seeds(10));

        assertEquals((1_000L << BITS) | 10, sequence.next(1_000L));
        assertEquals((1_000L << BITS) | 11, sequence.next(1_000L));
        assertEquals((1_000L << BITS) | 12, sequence.next(1_000L));
    }

    @Test
    void aClockThatGoesBackContinuesOnTheLastTimestamp() {
        MonotonicTimestampSequence sequence = new MonotonicTimestampSequence(seeds(10));

        long first = sequence.next(1_000L);
        long afterRegression = sequence.next(999L);

        assertEquals((1_000L << BITS) | 11, afterRegression);
        assertTrue(afterRegression > first);
    }

    @Test
    void aCounterThatOverflowsMovesToTheNextMillisecondWithAFreshSeed() {
        MonotonicTimestampSequence sequence = new MonotonicTimestampSequence(seeds(0x7FF, 3));
        long previous = sequence.next(1_000L);
        for (int counter = 0x800; counter <= 0xFFF; counter++) {
            long next = sequence.next(1_000L);
            assertEquals((1_000L << BITS) | counter, next);
            previous = next;
        }

        long overflow = sequence.next(1_000L);

        assertEquals((1_001L << BITS) | 3, overflow);
        assertTrue(overflow > previous);
    }

    @Test
    void theSeedSupplierIsRequired() {
        NullPointerException failure = assertThrows(NullPointerException.class, () -> new MonotonicTimestampSequence(null));
        assertEquals("counterSeed", failure.getMessage());
    }

    private static IntSupplier seeds(Integer... values) {
        Deque<Integer> remaining = new ArrayDeque<>(List.of(values));
        return remaining::removeFirst;
    }
}
