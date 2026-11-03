package com.morpheus.api.local;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A bounded pool of platform threads on which a call that can wait on the database runs, while its caller's virtual
 * thread parks (ADR-0159).
 *
 * <p>The SQLite driver waits for a busy database inside native code, where a virtual thread stays mounted on its
 * carrier whatever the JDK, and the carriers are as many as the processors. A call handed to a worker occupies the
 * worker, never a carrier. The whole call runs on one worker, so the thread-confined connection scope keeps its
 * meaning. Both servers use this one mechanism: the local server for every handler that can reach the database
 * ({@link DatabaseWorkOffloadingHttpServer}), the remote facade for the SQLite work it does itself.</p>
 *
 * <p>A call beyond the workers waits for a place, in arrival order, for at most the admission wait given at
 * construction, and is then refused by name ({@link Refusal#NO_WORKER_IN_TIME}). A place is held exactly while a
 * worker runs the call, so the pool's own queue stays empty and the only queue is the admission, fair and bounded in
 * time. The place is given back by the worker, when it has finished: a caller that leaves early does not free a
 * worker that is still taken.</p>
 *
 * <p>Only this class runs a {@link DatabaseCall}: a call run anywhere else would wait on the database on its caller's
 * thread ({@code DatabaseWorkArchitectureTest}).</p>
 */
public final class DatabaseCallWorkers implements AutoCloseable {

    /**
     * How many calls can be inside the database at once, per pool: eight, whatever the machine.
     *
     * <p>The count is not derived from the processors. A worker spends its time waiting on one SQLite file, not
     * computing: the database admits one writer at a time, and readers share the same file and the same disk. More
     * workers on a larger machine would add connections contending on that file, not throughput, and fewer on a
     * runner of two processors would refuse a burst that a workstation queues. Eight keeps reads concurrent, bounds
     * the connections and file handles a burst can open, and makes the admission behave the same on every machine.
     * It is unrelated to the carriers: workers are platform threads, and a caller frees its carrier by parking,
     * whatever their number.</p>
     */
    public static final int DATABASE_WORKERS = 8;

    /**
     * How long a call waits for a worker before it is refused: twice the five seconds a connection waits for a busy
     * database. A burst queues; a queue that does not move is told so, rather than growing without end.
     */
    public static final Duration ADMISSION_WAIT = Duration.ofSeconds(10);

    /**
     * Work that can wait on the database, run on a worker by {@link DatabaseCallWorkers} and by nothing else.
     *
     * <p>Implemented by named classes, never by a lambda: a lambda's body belongs to the class that writes it, where a
     * rule on the bytecode cannot tell whether it runs on a worker.</p>
     */
    public interface DatabaseCall<T> {
        T run() throws IOException;
    }

    /** Why a call was not run to its end on a worker. */
    public enum Refusal {
        /** Every worker stayed taken for the whole admission wait; the call was never handed to a worker. */
        NO_WORKER_IN_TIME,
        /**
         * The call was still running at its deadline. Its worker is interrupted and keeps its place until the call
         * returns: the outcome is unknown, and the work may still complete.
         */
        DEADLINE_PASSED
    }

    /** A call refused by name: the caller answers it, nothing is degraded silently. */
    public static final class RefusedCall extends RuntimeException {
        private final Refusal refusal;

        private RefusedCall(Refusal refusal, String message) {
            super(message);
            this.refusal = refusal;
        }

        public Refusal refusal() {
            return refusal;
        }
    }

    private final ExecutorService workers;
    private final Semaphore admission;
    private final Duration admissionWait;

    public DatabaseCallWorkers(String threadNamePrefix, int count, Duration admissionWait) {
        Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
        Objects.requireNonNull(admissionWait, "admissionWait");
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (admissionWait.isNegative()) {
            throw new IllegalArgumentException("admissionWait must not be negative");
        }
        AtomicInteger number = new AtomicInteger();
        this.workers = Executors.newFixedThreadPool(
                count,
                work -> Thread.ofPlatform()
                        .daemon(true)
                        .name(threadNamePrefix + number.incrementAndGet())
                        .unstarted(work));
        this.admission = new Semaphore(count, true);
        this.admissionWait = admissionWait;
    }

    /**
     * Runs a call that answers for itself - a handler that writes its own response - and waits for its worker to
     * finish, without a deadline.
     *
     * <p>The worker owns what the call answers on, so the caller has nothing it could answer in its place, and its
     * wait costs no carrier. The call is bounded by the admission wait, then by what bounds the call itself: the busy
     * timeout of each statement it runs.</p>
     */
    public <T> T runToItsEnd(DatabaseCall<T> call) throws IOException {
        return handOver(call).awaitOutcome();
    }

    /**
     * Runs a call whose outcome the caller answers, and waits at most {@code deadline} for it once a worker holds it.
     * Past the deadline the call is refused by name ({@link Refusal#DEADLINE_PASSED}).
     */
    public <T> T call(DatabaseCall<T> call, Duration deadline) throws IOException {
        Objects.requireNonNull(deadline, "deadline");
        if (deadline.isZero() || deadline.isNegative()) {
            throw new IllegalArgumentException("deadline must be positive");
        }
        return handOver(call).awaitOutcome(deadline);
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }

    private <T> CallOnAWorker<T> handOver(DatabaseCall<T> call) throws IOException {
        Objects.requireNonNull(call, "call");
        if (!admitted()) {
            throw new RefusedCall(
                    Refusal.NO_WORKER_IN_TIME,
                    "every database worker stayed taken for " + admissionWait.toMillis() + " ms");
        }
        CallOnAWorker<T> onAWorker = new CallOnAWorker<>(call, admission);
        try {
            workers.execute(onAWorker);
        } catch (RuntimeException refusedByThePool) {
            admission.release();
            throw refusedByThePool;
        }
        return onAWorker;
    }

    private boolean admitted() throws IOException {
        try {
            return admission.tryAcquire(admissionWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for a database worker", interrupted);
        }
    }

    /**
     * One call on a worker. The place is given back by the worker, when it has finished with the call: a caller that
     * is interrupted or whose deadline passes leaves, but its worker is still taken until the call returns, and a
     * place returned by the caller would admit a call no worker is free to run.
     */
    private static final class CallOnAWorker<T> implements Runnable {
        private final DatabaseCall<T> call;
        private final Semaphore admission;
        private final CompletableFuture<T> outcome = new CompletableFuture<>();
        private Thread worker;

        private CallOnAWorker(DatabaseCall<T> call, Semaphore admission) {
            this.call = call;
            this.admission = admission;
        }

        @Override
        public void run() {
            try {
                taken();
                outcome.complete(call.run());
            } catch (IOException | RuntimeException | Error raised) {
                outcome.completeExceptionally(raised);
            } finally {
                left();
                admission.release();
            }
        }

        private synchronized void taken() {
            worker = Thread.currentThread();
        }

        /** Under the same lock as the interruption, so that it cannot land on this worker's next call. */
        private synchronized void left() {
            worker = null;
            Thread.interrupted();
        }

        /**
         * Interrupts the worker that holds this call. A call no worker has taken yet is left to be run: it holds a
         * place, and only its worker can give the place back.
         */
        private synchronized void abandon() {
            if (worker != null) {
                worker.interrupt();
            }
        }

        private T awaitOutcome() throws IOException {
            try {
                return outcome.get();
            } catch (InterruptedException interrupted) {
                throw abandonedOnInterruption(interrupted);
            } catch (ExecutionException failed) {
                throw rethrown(failed.getCause());
            }
        }

        private T awaitOutcome(Duration deadline) throws IOException {
            try {
                return outcome.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                throw abandonedOnInterruption(interrupted);
            } catch (ExecutionException failed) {
                throw rethrown(failed.getCause());
            } catch (TimeoutException late) {
                abandon();
                throw new RefusedCall(
                        Refusal.DEADLINE_PASSED,
                        "the database call did not finish within " + deadline.toMillis() + " ms");
            }
        }

        private IOException abandonedOnInterruption(InterruptedException interrupted) {
            abandon();
            Thread.currentThread().interrupt();
            return new IOException("interrupted while a database worker handled the request", interrupted);
        }

        /** A call declares {@link IOException} and nothing else checked: what is neither that nor unchecked is an error. */
        private static IOException rethrown(Throwable cause) {
            if (cause instanceof IOException io) {
                return io;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw (Error) cause;
        }
    }
}
