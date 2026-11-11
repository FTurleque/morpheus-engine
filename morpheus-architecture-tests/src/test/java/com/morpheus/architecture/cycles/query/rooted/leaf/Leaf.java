package com.morpheus.architecture.cycles.query.rooted.leaf;

import com.morpheus.architecture.cycles.query.rooted.Ledger;

/** The second-level end of a cycle that passes through the root package. */
public final class Leaf {
    public int count() {
        return Ledger.count(this);
    }
}
