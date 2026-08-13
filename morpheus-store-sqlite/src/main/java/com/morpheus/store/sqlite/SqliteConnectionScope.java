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
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested SQLite connection scopes are not supported");
        }
        Path normalized = databasePath.toAbsolutePath().normalize();
        try {
            Connection physical = SqliteDatabaseSecurity.openPhysical(normalized, busyTimeoutMillis);
            State state = new State(normalized, busyTimeoutMillis, physical);
            recordPhysicalOpen();
            ACTIVE.set(state);
            return new SqliteConnectionScope(state);
        } catch (SQLException failure) {
            throw new IllegalStateException("cannot open SQLite operation scope", failure);
        }
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
            throw new SQLException("active SQLite scope is quarantined after a committed cleanup failure", state.quarantineCause);
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

    @Override
    public void close() {
        if (closed) return;
        State active = ACTIVE.get();
        if (active != state) {
            throw new IllegalStateException("SQLite connection scope must close on its owning thread");
        }

        // Only mark the scope closed after ownership has been established. A wrong-thread close attempt must
        // leave the owning thread able to perform the real cleanup later.
        ACTIVE.remove();
        closed = true;
        try {
            state.physical.close();
            recordPhysicalClose(state);
        } catch (SQLException failure) {
            if (state.physicalCountedActive) {
                state.physicalCountedActive = false;
                PHYSICAL_ACTIVE.decrementAndGet();
            }
            throw new IllegalStateException("cannot close SQLite operation scope", failure);
        }
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
            if (name.equals("close")) {
                logicalClosed.set(true);
                return null;
            }
            if (name.equals("isClosed")) {
                return logicalClosed.get() || state.quarantined || state.physical.isClosed();
            }
            if (name.equals("toString")) {
                return "ScopedSqliteConnection[" + state.databasePath + "]";
            }
            if (name.equals("hashCode")) return System.identityHashCode(proxy);
            if (name.equals("equals")) return proxy == args[0];
            if (logicalClosed.get()) throw new SQLException("logical SQLite connection is closed");
            if (state.quarantined) {
                throw new SQLException("SQLite connection scope is quarantined after a committed cleanup failure", state.quarantineCause);
            }
            try {
                return method.invoke(state.physical, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }

    private static final class State {
        private final Path databasePath;
        private final int busyTimeoutMillis;
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
