package com.morpheus.integration.mcp;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Waits for a state this module's tests can only observe by looking, with a budget and a diagnosis.
 *
 * <p>The states waited on here belong to operating-system processes -- a peer that must exit, a descendant that
 * must be reaped, a PID a child publishes through the filesystem. None of them can offer a latch to a different
 * process, so a bounded poll is not a shortcut around synchronisation; it is what observing another process
 * looks like. What was wrong with the hand-rolled copies of this loop was never the polling. It was that every
 * one of them expired saying only {@code "condition was not satisfied within PT10S"}, which names nothing that
 * was observed and leaves the reader to reconstruct by hand what the system was doing. A diagnosis on one
 * failure is worth more than ten attempts to make failure unlikely.</p>
 *
 * <p>So every expiry here reports what was actually seen: the last observed state, how long really elapsed,
 * how many samples were taken, and -- the distinction that usually decides where to look next -- whether the
 * state sat unchanged the whole time or was still moving when the budget ran out. A process list that never
 * changed points at a peer that never started or never died; one still changing at the deadline points at a
 * budget too short for a loaded runner, and those two have opposite fixes.</p>
 *
 * <p>Poll intervals are arguments rather than constants inside the loop, because the right interval is a
 * property of what is being observed, not of waiting in general. The named constants below carry the reason;
 * a call site that needs another value should say why in the same breath.</p>
 *
 * <p>This class is deliberately a per-module twin of {@code com.morpheus.api.BoundedWait} rather than a shared
 * artifact. This module has no MORPHEUS dependencies at all, by design, and giving it one so that two test
 * classes could share a helper would be a real architectural change made for a cosmetic reason.
 * {@code BoundedWaitOwnershipTest} keeps the two copies in step and stops the twelve hand-rolled helpers from
 * growing back.</p>
 */
final class BoundedWait {

    /**
     * Sampling interval for a state that belongs to an operating-system process.
     *
     * <p>Process liveness and reaping become observable within single-digit milliseconds, and reading them is
     * a local syscall, so the interval only needs to stay well below the transition being watched for.</p>
     */
    static final Duration PROCESS_TRANSITION_POLL = Duration.ofMillis(10);

    /**
     * Sampling interval for a value a child process publishes through the filesystem.
     *
     * <p>Publication is not atomic -- the directory entry can be visible before the bytes are -- so this is
     * sampled at the same rate as process transitions and the sample itself tolerates a partial read.</p>
     */
    static final Duration FILE_PUBLICATION_POLL = Duration.ofMillis(10);

    private BoundedWait() {
    }

    /**
     * Polls {@code condition} until it holds, and reports what {@code observedState} last said when it does not.
     *
     * @param what          what is being waited for, phrased so the failure reads as a sentence
     * @param budget        how long to keep sampling before failing
     * @param pollInterval  how long to pause between samples; see the constants above
     * @param condition     the state being waited for
     * @param observedState renders the state actually seen, for the failure message
     */
    // java:S2925 -- category one of three: a bounded poll of state this process can only
    // observe by looking. The state belongs to an operating-system process that cannot signal into this one,
    // so there is nothing to await; the deadline is what keeps a stuck peer a failure, not a hang.
    @SuppressWarnings("java:S2925")
    static void until(
            String what,
            Duration budget,
            Duration pollInterval,
            BooleanSupplier condition,
            Supplier<String> observedState) throws InterruptedException {
        Observation observation = new Observation(budget);
        while (true) {
            String state = observedState.get();
            observation.record(state);
            if (condition.getAsBoolean()) {
                return;
            }
            if (observation.expired()) {
                throw new AssertionError(observation.diagnose(what, state));
            }
            TimeUnit.NANOSECONDS.sleep(pollInterval.toNanos());
        }
    }

    /**
     * Samples until the sample satisfies {@code settled}, then returns that sample.
     *
     * <p>The sampled value is its own description of the observed state, which is what makes an expiry here
     * readable: the failure carries what was last read rather than the fact that time passed.</p>
     */
    // java:S2925 -- category one of three: a bounded poll of external state, exactly as above.
    @SuppressWarnings("java:S2925")
    static <T> T untilObserved(
            String what,
            Duration budget,
            Duration pollInterval,
            Callable<T> sample,
            Predicate<T> settled) throws Exception {
        Observation observation = new Observation(budget);
        while (true) {
            T observed = sample.call();
            String state = String.valueOf(observed);
            observation.record(state);
            if (settled.test(observed)) {
                return observed;
            }
            if (observation.expired()) {
                throw new AssertionError(observation.diagnose(what, state));
            }
            TimeUnit.NANOSECONDS.sleep(pollInterval.toNanos());
        }
    }

    /** Accumulates what a wait actually saw, so its expiry can say something more than that time passed. */
    private static final class Observation {
        private final Duration budget;
        private final long startedNanos = System.nanoTime();
        private final long deadlineNanos;
        private int samples;
        private int distinctStates;
        private String previousState;
        private long lastChangeNanos = System.nanoTime();

        private Observation(Duration budget) {
            this.budget = budget;
            this.deadlineNanos = startedNanos + budget.toNanos();
        }

        private void record(String state) {
            samples++;
            if (previousState == null || !previousState.equals(state)) {
                distinctStates++;
                lastChangeNanos = System.nanoTime();
                previousState = state;
            }
        }

        private boolean expired() {
            return System.nanoTime() >= deadlineNanos;
        }

        private String diagnose(String what, String lastState) {
            long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
            long sinceChangeMillis = (System.nanoTime() - lastChangeNanos) / 1_000_000L;
            String movement = distinctStates <= 1
                    ? "the observed state never changed across " + samples
                            + " samples, so nothing was progressing towards it"
                    : "the observed state was still changing (" + distinctStates
                            + " distinct states, the last change " + sinceChangeMillis
                            + "ms before the deadline), so the budget may simply be short for this runner";
            return what + " did not happen within the " + budget + " budget."
                    + " Last observed state: " + lastState + "."
                    + " Actually elapsed: " + elapsedMillis + "ms over " + samples + " samples."
                    + " Diagnosis: " + movement + ".";
        }
    }
}
