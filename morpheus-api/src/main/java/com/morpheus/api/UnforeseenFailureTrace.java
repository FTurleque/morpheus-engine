package com.morpheus.api;

import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.operability.SuppressedFailureFamily;

/**
 * What the server keeps of a failure it answers with a generic internal error.
 *
 * <p>The caller is told nothing of such a failure, on purpose: its message is not written for a caller and may name a
 * server location. The server used to keep nothing either - no counter, no event - so an operator could not tell that
 * requests were dying, let alone of what. The response does not change; the failure is counted and, when a sink
 * listens, reported with its type.</p>
 */
final class UnforeseenFailureTrace {
    private UnforeseenFailureTrace() {
    }

    static void record(RuntimeException failure) {
        LocalOperationalRuntime.recorder().suppressed(SuppressedFailureFamily.HTTP_INTERNAL_ERROR, failure);
    }
}
