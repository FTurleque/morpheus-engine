package com.morpheus.domain.identity;

import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Millisecond timestamp and 12-bit counter of a UUIDv7 that only grow within one process (RFC 9562, section 6.2,
 * method 1). A new millisecond reseeds the counter with its leftmost bit cleared, which leaves at least 2048 increments
 * before it overflows. A clock that goes back continues on the last timestamp handed out; an overflow advances that
 * timestamp by one millisecond.
 */
final class MonotonicTimestampSequence {
    static final int COUNTER_BITS = 12;
    private static final int COUNTER_MAX = (1 << COUNTER_BITS) - 1;
    private static final int SEED_MASK = COUNTER_MAX >>> 1;
    private static final long TIMESTAMP_MASK = 0x0000FFFFFFFFFFFFL;

    private final IntSupplier counterSeed;
    private long lastMillis = -1;
    private int counter;

    MonotonicTimestampSequence(IntSupplier counterSeed) {
        this.counterSeed = Objects.requireNonNull(counterSeed, "counterSeed");
    }

    /** Returns the timestamp shifted left by {@link #COUNTER_BITS}, joined with the counter. */
    synchronized long next(long nowMillis) {
        long millis = nowMillis & TIMESTAMP_MASK;
        if (millis > lastMillis) {
            lastMillis = millis;
            counter = counterSeed.getAsInt() & SEED_MASK;
        } else if (counter < COUNTER_MAX) {
            counter++;
        } else {
            lastMillis++;
            counter = counterSeed.getAsInt() & SEED_MASK;
        }
        return (lastMillis << COUNTER_BITS) | counter;
    }
}
