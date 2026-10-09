package com.morpheus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Holds a natural order in both directions and at equality, which is what makes a published collection stable. */
public final class OrderingAssertions {
    private OrderingAssertions() {
    }

    public static <T extends Comparable<T>> void assertStrictlyOrdered(T lower, T higher) {
        assertTrue(lower.compareTo(higher) < 0, () -> lower + " must order before " + higher);
        assertTrue(higher.compareTo(lower) > 0, () -> higher + " must order after " + lower);
        assertEquals(0, lower.compareTo(lower), () -> lower + " must order equal to itself");
    }
}
