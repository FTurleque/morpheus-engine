package com.morpheus.application.operability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the property the shutdown paths depend on: the resource released last is released even when an earlier
 * release fails. Written as a plain sequence of close calls, that resource -- the exclusive database lease --
 * was the one a failure skipped, so the process kept the database reserved while reporting itself shut down.
 */
class ExhaustiveShutdownTest {

    @Test
    void everyLaterResourceIsStillReleasedWhenAnEarlierReleaseFails() {
        List<String> released = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> ExhaustiveShutdown.releaseAll(
                "cannot shut down",
                () -> released.add("socket"),
                () -> {
                    throw new IllegalStateException("executor refused to stop");
                },
                () -> released.add("local server"),
                () -> released.add("lease")));

        assertEquals(List.of("socket", "local server", "lease"), released);
    }

    @Test
    void theFirstFailureIsWhatPropagatesAndTheRestArriveAttachedToIt() {
        RuntimeException first = new IllegalStateException("executor refused to stop");
        RuntimeException second = new IllegalArgumentException("lease refused to release");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> ExhaustiveShutdown.releaseAll(
                "cannot shut down",
                () -> {
                    throw first;
                },
                () -> {
                    throw second;
                }));

        assertSame(first, thrown, "the caller must learn why shutdown failed, not why a later cleanup did");
        assertEquals(List.of(second), List.of(thrown.getSuppressed()));
    }

    @Test
    void anErrorDoesNotStopTheRemainingReleasesAndIsRethrownAsItself() {
        List<String> released = new ArrayList<>();
        LinkageError first = new LinkageError("store class failed to initialize");

        LinkageError thrown = assertThrows(LinkageError.class, () -> ExhaustiveShutdown.releaseAll(
                "cannot shut down",
                () -> {
                    throw first;
                },
                () -> released.add("lease")));

        assertSame(first, thrown, "an Error must not be repackaged as an IllegalStateException");
        assertEquals(List.of("lease"), released);
    }

    @Test
    void aCheckedFailureIsReportedUnderTheNameOfTheRuntimeBeingShutDown() {
        IOException cause = new IOException("cannot flush");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> ExhaustiveShutdown.releaseAll(
                "cannot close API runtime resource",
                () -> {
                    throw cause;
                }));

        assertEquals("cannot close API runtime resource", thrown.getMessage());
        assertSame(cause, thrown.getCause());
    }

    @Test
    void aResourceThatWasNeverAcquiredIsSkippedRatherThanFailingTheRollback() {
        List<String> released = new ArrayList<>();

        assertDoesNotThrow(() -> ExhaustiveShutdown.releaseAll(
                "cannot close CLI runtime resource",
                null,
                () -> released.add("scope"),
                null));

        assertEquals(List.of("scope"), released);
    }
}
