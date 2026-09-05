package com.morpheus.store.sqlite;

import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.operability.OperationalEventCode;

import java.util.Objects;

/**
 * Process-local counters that make SQLite write contention visible before it becomes an outage.
 *
 * <p>SQLite serializes writers, so contention does not announce itself: a busy database is a slower one until
 * the busy timeout is exhausted and an operation fails outright. Between those two states there is no signal at
 * all, which is exactly the range an operator needs to see coming. These counters give it one -- rolled-back
 * transactions, transactions that actually hit {@code SQLITE_BUSY}/{@code SQLITE_LOCKED}, and how long the
 * slowest transaction took -- and they reach {@code GET /api/v1/metrics} through the process-local operational
 * runtime that already carries every other MORPHEUS counter. No exporter and no network transport is implied.</p>
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

    private SqliteContentionMetrics() {
    }

    static void transactionStarted() {
        LocalOperationalRuntime.metrics().increment(TRANSACTIONS_STARTED);
    }

    static void transactionCommitted(long durationNanos) {
        LocalOperationalRuntime.metrics().increment(TRANSACTIONS_COMMITTED);
        LocalOperationalRuntime.metrics().recordDurationNanos(TRANSACTION_DURATION, Math.max(0L, durationNanos));
    }

    static void transactionRolledBack(long durationNanos, Throwable failure) {
        LocalOperationalRuntime.metrics().increment(TRANSACTIONS_ROLLED_BACK);
        LocalOperationalRuntime.metrics().recordDurationNanos(TRANSACTION_DURATION, Math.max(0L, durationNanos));
        recordIfContended(failure, CONTENDED_TRANSACTIONS);
    }

    static void connectionOpenFailed(Throwable failure) {
        recordIfContended(failure, CONTENDED_CONNECTION_OPENS);
    }

    private static void recordIfContended(Throwable failure, String counter) {
        Objects.requireNonNull(counter, "counter");
        if (failure == null) return;
        if (CLASSIFIER.classify(failure).filter(OperationalEventCode.DATABASE_LOCKED::equals).isPresent()) {
            LocalOperationalRuntime.metrics().increment(counter);
        }
    }
}
