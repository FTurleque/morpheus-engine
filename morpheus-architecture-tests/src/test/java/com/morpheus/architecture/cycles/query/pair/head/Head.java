package com.morpheus.architecture.cycles.query.pair.head;

import com.morpheus.architecture.cycles.query.pair.tail.Marker;
import com.morpheus.architecture.cycles.query.pair.tail.Tail;

/** The return edge of a two-package cycle under a query root: ignored by the rule only when it is listed. */
public final class Head {
    private Head() {
    }

    public static int weigh(Tail.Nested nested, Marker marker) {
        return nested.hashCode() + marker.hashCode();
    }
}
