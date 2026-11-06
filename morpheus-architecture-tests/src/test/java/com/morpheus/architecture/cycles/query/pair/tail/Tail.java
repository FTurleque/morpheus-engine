package com.morpheus.architecture.cycles.query.pair.tail;

import com.morpheus.architecture.cycles.query.pair.head.Head;
import com.morpheus.architecture.cycles.query.pair.head.Limits;

/** The natural direction of the two-package cycle: a tail depends on its head. */
public final class Tail {
    public int size(Nested nested, Marker marker) {
        return Math.min(Head.weigh(nested, marker), Limits.MAX);
    }

    /** A nested type, which the rule and the measurement bring back to its top-level type. */
    public static final class Nested {
    }
}
