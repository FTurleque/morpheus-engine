package com.morpheus.application.operability;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the ownership contract the HTTP bootstraps rely on.
 *
 * <p>These are the orderings that leave a socket bound or an executor alive when they go wrong, so each is
 * asserted directly rather than through a server that cannot be made to fail on demand.</p>
 */
class StartupOwnershipTest {
    @Test
    void aFailureAfterAcquisitionReleasesEverythingInReverseOrder() {
        List<String> released = new ArrayList<>();

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> failAfterKeeping(released));

        assertEquals("service construction failed", failure.getMessage());
        assertEquals(List.of("executor", "socket"), released,
                "the most recent acquisition is released first, mirroring how it was built");
    }

    @Test
    void nothingIsReleasedOnceOwnershipHasBeenTransferred() {
        List<String> released = new ArrayList<>();

        try (StartupOwnership owned = new StartupOwnership()) {
            owned.keep("socket", released::add);
            owned.keep("executor", released::add);
            owned.transferred();
        }

        assertEquals(List.of(), released, "the assembled runtime owns these now");
    }

    @Test
    void keepReturnsTheResourceSoAcquisitionStaysASingleExpression() {
        try (StartupOwnership owned = new StartupOwnership()) {
            Object resource = new Object();
            assertSame(resource, owned.keep(resource, ignored -> { }));
            owned.transferred();
        }
    }

    /** The reason startup failed must survive; a failure to clean up is secondary information. */
    @Test
    void aReleaseFailureIsReportedAsSuppressedAndNeverReplacesThePrimaryFailure() {
        AtomicBoolean secondReleased = new AtomicBoolean();

        RuntimeException primary = assertThrows(
                RuntimeException.class,
                () -> failWithAFailingRelease(secondReleased));

        assertEquals("port must be between 0 and 65535", primary.getMessage());
        assertTrue(secondReleased.get(), "a failing release must not stop the remaining ones");
        assertEquals(1, primary.getSuppressed().length);
        assertEquals(
                "cannot release a partially assembled MORPHEUS runtime",
                primary.getSuppressed()[0].getMessage());
        assertEquals("shutdownNow failed", primary.getSuppressed()[0].getSuppressed()[0].getMessage());
    }

    /** An Error during assembly is not swallowed, and must not skip the release either. */
    @Test
    void anErrorDuringAssemblyStillReleasesAndStillPropagates() {
        AtomicBoolean released = new AtomicBoolean();

        ExceptionInInitializerError raised = assertThrows(
                ExceptionInInitializerError.class,
                () -> failWithAnError(released));

        assertEquals("static initializer failed", raised.getMessage());
        assertTrue(released.get(), "an Error must still free the socket before it leaves the bootstrap");
    }

    /** Forgetting the transfer must fail loudly rather than hand back a runtime whose socket was just closed. */
    @Test
    void forgettingTheTransferReleasesRatherThanSilentlySucceeding() {
        AtomicBoolean released = new AtomicBoolean();

        try (StartupOwnership owned = new StartupOwnership()) {
            owned.keep("socket", ignored -> released.set(true));
        }

        assertTrue(released.get());
    }

    @Test
    void keepActionRegistersAReleaseForAHandleTheCallerHoldsItself() {
        AtomicBoolean released = new AtomicBoolean();

        assertThrows(
                IllegalStateException.class,
                () -> failAfterKeepingAnAction(released));

        assertTrue(released.get());
    }

    @Test
    void aResourceOrReleaseThatIsNullIsRejectedAtRegistration() {
        try (StartupOwnership owned = new StartupOwnership()) {
            assertThrows(NullPointerException.class, () -> owned.keep(null, ignored -> { }));
            assertThrows(NullPointerException.class, () -> owned.keep("socket", null));
            assertThrows(NullPointerException.class, () -> owned.keepAction(null));
            owned.transferred();
        }
    }

    @Test
    void closingTwiceAfterAReleaseDoesNotReleaseAgain() {
        List<String> released = new ArrayList<>();
        StartupOwnership owned = new StartupOwnership();
        owned.keep("socket", released::add);

        owned.close();
        owned.close();

        assertEquals(List.of("socket"), released, "a second close must not re-run a release");
        assertFalse(released.size() > 1);
    }

    private static void failAfterKeeping(List<String> released) {
        try (StartupOwnership owned = new StartupOwnership()) {
            owned.keep("socket", released::add);
            owned.keep("executor", released::add);
            throw new IllegalStateException("service construction failed");
        }
    }

    private static void failWithAFailingRelease(AtomicBoolean secondReleased) {
        try (StartupOwnership owned = new StartupOwnership()) {
            owned.keep("socket", ignored -> secondReleased.set(true));
            owned.keep("executor", ignored -> {
                throw new IllegalStateException("shutdownNow failed");
            });
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
    }

    private static void failWithAnError(AtomicBoolean released) {
        try (StartupOwnership owned = new StartupOwnership()) {
            owned.keep("socket", ignored -> released.set(true));
            throw new ExceptionInInitializerError("static initializer failed");
        }
    }

    private static void failAfterKeepingAnAction(AtomicBoolean released) {
        try (StartupOwnership owned = new StartupOwnership()) {
            owned.keepAction(() -> released.set(true));
            throw new IllegalStateException("bind failed");
        }
    }
}
