package com.morpheus.integration.mcp;

import reactor.core.scheduler.Scheduler;

/**
 * Disposes every scheduler a transport owns, even when disposing one of them fails.
 *
 * <p>Written as a sequence of {@code dispose()} calls, the first failure ends the method and the schedulers
 * after it keep their threads for the life of the process -- one set per configured MCP peer.</p>
 *
 * <p>This repeats what {@code com.morpheus.application.operability.ExhaustiveShutdown} already does, because
 * this module deliberately depends on neither the domain nor the application: adding that edge to reach a
 * helper would be a larger change than the helper. The rule is the same one, kept where it can be applied.</p>
 */
final class SchedulerRelease {

    private SchedulerRelease() {
    }

    /** Disposes all of them, keeps the first failure, and attaches the rest to it. */
    // java:S1181 catches Error deliberately: the remaining schedulers must still be disposed when one fails on
    // a LinkageError. Nothing is swallowed -- the first failure is what propagates.
    @SuppressWarnings("java:S1181")
    static void disposeAll(Scheduler... schedulers) {
        Throwable primary = null;
        for (Scheduler scheduler : schedulers) {
            if (scheduler == null) {
                continue;
            }
            try {
                scheduler.dispose();
            } catch (RuntimeException | Error failure) {
                if (primary == null) {
                    primary = failure;
                } else if (primary != failure) {
                    primary.addSuppressed(failure);
                }
            }
        }
        if (primary instanceof RuntimeException unchecked) {
            throw unchecked;
        }
        if (primary instanceof Error error) {
            throw error;
        }
    }
}
