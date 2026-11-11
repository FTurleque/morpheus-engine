package com.morpheus.architecture.cycles.fixture.beta;

import com.morpheus.architecture.cycles.fixture.alpha.Alpha;

/** The other end of the two-package cycle that the application cycle rule must refuse. */
public final class Beta {
    public int depth() {
        return Alpha.depth(this);
    }
}
