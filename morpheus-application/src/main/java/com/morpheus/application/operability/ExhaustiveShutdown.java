package com.morpheus.application.operability;

import java.util.Objects;

/**
 * Releases every resource a runtime owns, even when releasing one of them fails.
 *
 * <p>Shutdown written as a plain sequence of {@code close()} calls stops at the first failure, and everything
 * after it stays held: a bound socket, a thread pool, an exclusive database lease. The resource that leaks is
 * the one released last, which is usually the one that matters most, because a held lease keeps the whole
 * database unusable for the rest of the process.</p>
 *
 * <p>Each release runs regardless of what the previous ones did. The first failure is what propagates, unchanged
 * when it already is unchecked, so the caller sees why shutdown failed rather than why a later cleanup did; the
 * rest arrive attached to it as suppressed. {@link StartupOwnership} does the same job for the resources
 * acquired while a runtime is still being assembled, where the failure to report is the assembly failure.</p>
 */
public final class ExhaustiveShutdown {

    private ExhaustiveShutdown() {
    }

    /**
     * Releases every resource in order, skipping nulls, and reports the failures together.
     *
     * @param failureMessage names the runtime being shut down, used when a release throws a checked exception
     * @param resources      the releases to perform, in the order they must happen
     */
    // java:S1181 catches Error deliberately: an executor or a lease must still be released when an earlier
    // release failed on a LinkageError. Nothing is swallowed -- the first failure is what propagates.
    @SuppressWarnings("java:S1181")
    public static void releaseAll(String failureMessage, AutoCloseable... resources) {
        Objects.requireNonNull(failureMessage, "failureMessage");
        Objects.requireNonNull(resources, "resources");
        Throwable primary = null;
        for (AutoCloseable resource : resources) {
            if (resource == null) {
                continue;
            }
            try {
                resource.close();
            } catch (RuntimeException | Error failure) {
                primary = keepFirst(primary, failure);
            } catch (Exception failure) {
                primary = keepFirst(primary, new IllegalStateException(failureMessage, failure));
            }
        }
        if (primary instanceof RuntimeException unchecked) {
            throw unchecked;
        }
        if (primary instanceof Error error) {
            throw error;
        }
    }

    /**
     * Two releases can rethrow the same instance -- a shared sentinel, or one resource delegating to another.
     * {@link Throwable#addSuppressed} rejects that with an {@link IllegalArgumentException}, which would escape
     * the loop and leave every remaining resource unreleased: the one failure this class exists to prevent.
     */
    private static Throwable keepFirst(Throwable primary, Throwable failure) {
        if (primary == null) {
            return failure;
        }
        if (primary != failure) {
            primary.addSuppressed(failure);
        }
        return primary;
    }
}
