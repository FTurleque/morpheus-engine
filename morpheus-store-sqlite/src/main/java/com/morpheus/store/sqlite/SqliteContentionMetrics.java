package com.morpheus.store.sqlite;

import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.operability.OperationalEventCode;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Objects;

/**
 * Counters that make SQLite write contention visible before it becomes an outage.
 *
 * <p>SQLite serializes writers, so contention does not announce itself: a busy database is a slower one until
 * the busy timeout is exhausted and an operation fails outright. Between those two states there is no signal at
 * all, which is exactly the range an operator needs to see coming. These counters give it one -- rolled-back
 * transactions, transactions that actually hit {@code SQLITE_BUSY}/{@code SQLITE_LOCKED}, and how long the
 * slowest transaction took -- and they reach {@code GET /api/v1/metrics} through the operational recorder the
 * connection was opened with. No exporter and no network transport is implied.</p>
 *
 * <p>The recorder travels with the connection. Whoever opens a connection or a {@link SqliteConnectionScope} names
 * it, and every transaction run on that connection writes there: the transaction runner asks the connection, never
 * the process. A connection whose opener named none writes to {@link #processDefault()}, which is what the process
 * serves. A connection MORPHEUS did not open carries none, and its transactions are counted where nobody reads.</p>
 *
 * <p>These are diagnostics, never control flow: nothing here decides whether an operation retries or fails.</p>
 *
 * <p>Scope is deliberately stated rather than implied. What is counted is every explicit transaction and every
 * physical connection open -- the paths where a write lock is actually held long enough to contend, which is
 * every multi-statement mutation MORPHEUS performs. A single-statement autocommit write is not counted: seeing
 * it would mean proxying every JDBC statement through the lease guard, and that guard is what releases the
 * database lease. A counter is not worth changing it.</p>
 */
final class SqliteContentionMetrics {
    static final String TRANSACTIONS_STARTED = "sqlite.transaction.started";
    static final String TRANSACTIONS_COMMITTED = "sqlite.transaction.committed";
    static final String TRANSACTIONS_ROLLED_BACK = "sqlite.transaction.rolled_back";
    static final String TRANSACTION_DURATION = "sqlite.transaction.duration";
    static final String CONTENDED_TRANSACTIONS = "sqlite.contention.busy_or_locked";
    static final String CONTENDED_CONNECTION_OPENS = "sqlite.contention.connection_open";

    private static final SqliteFailureClassifier CLASSIFIER = new SqliteFailureClassifier();

    private final OperationalRecorder recorder;
    private final OperationalMetrics metrics;

    SqliteContentionMetrics(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.metrics = recorder.metrics();
    }

    /** What a connection writes to when its opener named no recorder: the counters the process serves. */
    static SqliteContentionMetrics processDefault() {
        return new SqliteContentionMetrics(LocalOperationalRuntime.recorder());
    }

    /**
     * The counters of the opener of this connection. They are read from the handler MORPHEUS wrapped the connection
     * in, without calling the connection: a connection that is not one of ours answers nothing here.
     */
    static SqliteContentionMetrics of(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        if (Proxy.isProxyClass(connection.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(connection);
            if (handler instanceof Carrier carrier) {
                return carrier.contention();
            }
        }
        return new SqliteContentionMetrics(OperationalRecorder.noop());
    }

    /** A connection handler that knows which counters its connection was opened with. */
    interface Carrier {
        SqliteContentionMetrics contention();
    }

    OperationalRecorder recorder() {
        return recorder;
    }

    void transactionStarted() {
        metrics.increment(TRANSACTIONS_STARTED);
    }

    void transactionCommitted(long durationNanos) {
        metrics.increment(TRANSACTIONS_COMMITTED);
        metrics.recordDurationNanos(TRANSACTION_DURATION, Math.max(0L, durationNanos));
    }

    void transactionRolledBack(long durationNanos, Throwable failure) {
        metrics.increment(TRANSACTIONS_ROLLED_BACK);
        metrics.recordDurationNanos(TRANSACTION_DURATION, Math.max(0L, durationNanos));
        recordIfContended(failure, CONTENDED_TRANSACTIONS);
    }

    void connectionOpenFailed(Throwable failure) {
        recordIfContended(failure, CONTENDED_CONNECTION_OPENS);
    }

    private void recordIfContended(Throwable failure, String counter) {
        if (failure == null) return;
        if (CLASSIFIER.classify(failure).filter(OperationalEventCode.DATABASE_LOCKED::equals).isPresent()) {
            metrics.increment(counter);
        }
    }
}
