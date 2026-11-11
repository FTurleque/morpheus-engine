package com.morpheus.architecture.cycles.query.rooted;

import com.morpheus.architecture.cycles.query.rooted.leaf.Leaf;

/** A class of the root package itself, on a cycle with one of its second-level packages. */
public final class Ledger {
    private Ledger() {
    }

    public static int count(Leaf leaf) {
        return leaf.hashCode();
    }
}
