package com.morpheus.architecture.cycles.fixture.alpha;

import com.morpheus.architecture.cycles.fixture.beta.Beta;

/** One end of a two-package cycle that the application cycle rule must refuse. */
public final class Alpha {
    private Alpha() {
    }

    public static int depth(Beta beta) {
        return beta.hashCode();
    }
}
