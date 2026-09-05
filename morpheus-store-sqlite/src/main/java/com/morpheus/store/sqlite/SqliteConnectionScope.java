package com.morpheus.store.sqlite;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Explicit thread-confined scope that lets multiple SQLite store adapters share one physical connection.
 * Logical connections retain normal close semantics without owning the physical connection.
 */
public final class SqliteConnectionScope implements AutoCloseable {
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();
    private static final AtomicLong PHYSICAL_OPENED = new AtomicLong();
    private static final AtomicLong PHYSICAL_CLOSED = new AtomicLong();
    private static final AtomicInteger PHYSICAL_ACTIVE = new AtomicInteger();
    private static final AtomicInteger PHYSICAL_PEAK = new AtomicInteger();

    private final State state;
    private boolean closed;

    private SqliteConnectionScope(State state) {
        this.state = state;
    }

    public static SqliteConnectionScope open(Path databasePath) {
        return open(databasePath, SqliteDatabaseSecurity.DEFAULT_BUSY_TIMEOUT_MILLIS);
    }

    public static SqliteConnectionScope open(Path databasePath, int busyTimeoutMillis) {
        Objects.requireNonNull(databasePath, "databasePath");
        // Rejected before the connection is opened: refusing afterwards would leak the connection it opened.
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested SQLite connection scopes are not supported");
        }
        Path normalized = databasePath.toAbsolutePath().normalize();
        try {
            return adopt(normalized, busyTimeoutMillis,
                    SqliteDatabaseSecurity.openPhysical(normalized, busyTimeoutMillis));
        } catch (SQLException failure) {
            throw new IllegalStateException("cannot open SQLite operation scope", failure);
        }
    }

    /**
     * Opens a scope over a physical connection the caller established and now hands over.
     *
     * <p>{@link #close()} has to behave when the driver fails to release the connection, and a healthy SQLite
     * file never produces that failure. Taking the connection as a parameter is how that branch is reached, the
     * same way {@link SqliteTransactionRunner} takes the connection whose cleanup failures it must survive.</p>
     */
    static SqliteConnectionScope adopt(Path databasePath, int busyTimeoutMillis, Connection physical) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(physical, "physical");
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested SQLite connection scopes are not supported");
        }
        State state = new State(databasePath.toAbsolutePath().normalize(), busyTimeoutMillis, physical);
        recordPhysicalOpen();
        ACTIVE.set(state);
        return new SqliteConnectionScope(state);
    }

    public int logicalConnectionsBorrowed() {
        return state.borrows.get();
    }

    public int schemaInitializations() {
        return state.schemaInitializations;
    }

    /** Process-level pressure diagnostics for scoped API sessions. */
    public static Diagnostics diagnostics() {
        return new Diagnostics(
                PHYSICAL_OPENED.get(),
                PHYSICAL_CLOSED.get(),
                PHYSICAL_ACTIVE.get(),
                PHYSICAL_PEAK.get());
    }

    static boolean schemaReadyIfActive() {
        State state = ACTIVE.get();
        return state != null && state.schemaReady;
    }

    static void markSchemaReadyIfActive() {
        State state = ACTIVE.get();
        if (state != null && !state.schemaReady) {
            state.schemaReady = true;
            state.schemaInitializations++;
        }
    }

    static Connection borrowIfActive(Path databasePath, int busyTimeoutMillis) throws SQLException {
        State state = ACTIVE.get();
        if (state == null) return null;
        Path normalized = databasePath.toAbsolutePath().normalize();
        if (!state.databasePath.equals(normalized)) {
            throw new SQLException("active SQLite scope is bound to a different database");
        }
        if (state.busyTimeoutMillis != busyTimeoutMillis) {
            throw new SQLException("active SQLite scope uses a different busy timeout");
        }
        if (state.quarantined) {
            throw new SQLException("active SQLite scope is quarantined after a failed connection cleanup", state.quarantineCause);
        }
        state.borrows.incrementAndGet();
        return (Connection) Proxy.newProxyInstance(
                SqliteConnectionScope.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ScopedConnectionHandler(state));
    }

    /**
     * Replace the physical connection after a mutation was durably committed but JDBC cleanup failed.
     * Existing logical proxies delegate through {@link State#physical}, so they transparently use the replacement.
     */
    static boolean recoverAfterCommittedCleanupFailure(Connection logicalConnection, Throwable originalFailure) {
        State active = ACTIVE.get();
        if (active == null || !belongsTo(logicalConnection, active)) {
            return false;
        }
        if (active.quarantined) {
            return false;
        }

        try {
            active.physical.close();
            recordPhysicalClose(active);
        } catch (SQLException | RuntimeException | Error closeFailure) {
            suppress(originalFailure, closeFailure);
            quarantine(active, originalFailure);
            return false;
        }

        try {
            active.physical = SqliteDatabaseSecurity.openPhysical(active.databasePath, active.busyTimeoutMillis);
            active.physicalCountedActive = true;
            recordPhysicalOpen();
            // Schema is durable in the database and was already initialized for this scope.
            return true;
        } catch (SQLException | RuntimeException | Error reopenFailure) {
            suppress(originalFailure, reopenFailure);
            quarantine(active, originalFailure);
            return false;
        }
    }

    /**
     * Releases the physical connection, and only claims to have released it once that has happened.
     *
     * <p>The scope used to detach from the thread and mark itself closed before the JDBC close ran, then count
     * the connection as no longer active whether or not it had been released. A scope whose close failed
     * therefore reported a connection it still held as gone, and the owning thread could not retry: the second
     * call returned immediately. Detaching now happens after a successful release, so a failed close leaves a
     * scope that is honestly still open, refuses further work through its connections, and can be closed
     * again.</p>
     */
    // java:S1181 catches Error deliberately: the scope must record that it still holds the connection when the
    // driver fails on a LinkageError, exactly as it does for a SQLException. The failure keeps propagating.
    @Override
    @SuppressWarnings("java:S1181")
    public void close() {
        if (closed) return;
        State active = ACTIVE.get();
        if (active != state) {
            throw new IllegalStateException("SQLite connection scope must close on its owning thread");
        }

        try {
            state.physical.close();
        } catch (SQLException failure) {
            quarantine(state, failure);
            throw new IllegalStateException("cannot close SQLite operation scope", failure);
        } catch (RuntimeException | Error failure) {
            quarantine(state, failure);
            throw failure;
        }

        // Past this point the connection is released, so the scope may detach from the thread, declare itself
        // closed, and count the release once -- guarded against a second count by physicalCountedActive.
        ACTIVE.remove();
        closed = true;
        recordPhysicalClose(state);
    }

    private static boolean belongsTo(Connection connection, State state) {
        if (connection == null || !Proxy.isProxyClass(connection.getClass())) {
            return false;
        }
        try {
            InvocationHandler handler = Proxy.getInvocationHandler(connection);
            return handler instanceof ScopedConnectionHandler scoped && scoped.state == state;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void quarantine(State state, Throwable cause) {
        state.quarantined = true;
        state.quarantineCause = cause;
    }

    private static void recordPhysicalOpen() {
        PHYSICAL_OPENED.incrementAndGet();
        int active = PHYSICAL_ACTIVE.incrementAndGet();
        PHYSICAL_PEAK.accumulateAndGet(active, Math::max);
    }

    private static void recordPhysicalClose(State state) {
        if (!state.physicalCountedActive) return;
        state.physicalCountedActive = false;
        PHYSICAL_CLOSED.incrementAndGet();
        PHYSICAL_ACTIVE.decrementAndGet();
    }

    private static void suppress(Throwable primary, Throwable secondary) {
        if (primary != secondary) primary.addSuppressed(secondary);
    }

    public record Diagnostics(long opened, long closed, int active, int peak) {
        public Diagnostics {
            if (opened < 0 || closed < 0 || active < 0 || peak < 0) {
                throw new IllegalArgumentException("SQLite connection diagnostics must not be negative");
            }
        }
    }

    private static final class ScopedConnectionHandler implements InvocationHandler {
        private final State state;
        private final AtomicBoolean logicalClosed = new AtomicBoolean();

        private ScopedConnectionHandler(State state) {
            this.state = state;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            String name = method.getName();
            // Identity is not database access: comparing or printing a proxy touches no shared state, so it stays
            // available anywhere. Everything below reaches the one physical SQLite connection this scope owns.
            if (name.equals("toString")) {
                return "ScopedSqliteConnection[" + state.databasePath + "]";
            }
            if (name.equals("hashCode")) return System.identityHashCode(proxy);
            if (name.equals("equals")) return proxy == args[0];
            requireOwningThread(name);
            if (name.equals("close")) {
                logicalClosed.set(true);
                return null;
            }
            if (name.equals("isClosed")) {
                return logicalClosed.get() || state.quarantined || state.physical.isClosed();
            }
            if (logicalClosed.get()) throw new SQLException("logical SQLite connection is closed");
            if (state.quarantined) {
                throw new SQLException("SQLite connection scope is quarantined after a failed connection cleanup", state.quarantineCause);
            }
            try {
                return method.invoke(state.physical, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        /**
         * The scope shares one physical SQLite connection between every store adapter on the owning thread, which
         * is only safe because that thread is the only one using it. A logical connection handed to another thread
         * would drive that same connection concurrently -- interleaving statements and transaction state on a
         * handle that is not built for it. Failing here is loud; the corruption it prevents would not be.
         */
        private void requireOwningThread(String operation) throws SQLException {
            Thread current = Thread.currentThread();
            if (current != state.owner) {
                throw new SQLException(
                        "scoped SQLite connection is confined to the thread that opened its scope: "
                                + operation + " was called from '" + current.getName()
                                + "' but the scope belongs to '" + state.owner.getName() + "'");
            }
        }
    }

    private static final class State {
        private final Path databasePath;
        private final int busyTimeoutMillis;
        /** The thread that opened the scope; the only one allowed to reach the physical connection. */
        private final Thread owner = Thread.currentThread();
        private Connection physical;
        private final AtomicInteger borrows = new AtomicInteger();
        private boolean schemaReady;
        private int schemaInitializations;
        private boolean physicalCountedActive = true;
        private boolean quarantined;
        private Throwable quarantineCause;

        private State(Path databasePath, int busyTimeoutMillis, Connection physical) {
            this.databasePath = databasePath;
            this.busyTimeoutMillis = busyTimeoutMillis;
            this.physical = physical;
        }
    }
}
