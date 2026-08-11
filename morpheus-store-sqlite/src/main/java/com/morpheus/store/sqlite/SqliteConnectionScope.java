package com.morpheus.store.sqlite;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Explicit thread-confined scope that lets multiple SQLite store adapters share one physical connection.
 * Logical connections retain normal close semantics without owning the physical connection.
 */
public final class SqliteConnectionScope implements AutoCloseable {
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

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
            ACTIVE.set(state);
            return new SqliteConnectionScope(state);
        } catch (SQLException failure) {
            throw new IllegalStateException("cannot open SQLite operation scope", failure);
        }
    }

    public int logicalConnectionsBorrowed() {
        return state.borrows.get();
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
        state.borrows.incrementAndGet();
        AtomicBoolean logicalClosed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                SqliteConnectionScope.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("close")) {
                        logicalClosed.set(true);
                        return null;
                    }
                    if (name.equals("isClosed")) {
                        return logicalClosed.get() || state.physical.isClosed();
                    }
                    if (name.equals("toString")) {
                        return "ScopedSqliteConnection[" + state.databasePath + "]";
                    }
                    if (name.equals("hashCode")) return System.identityHashCode(proxy);
                    if (name.equals("equals")) return proxy == args[0];
                    if (logicalClosed.get()) throw new SQLException("logical SQLite connection is closed");
                    try {
                        return method.invoke(state.physical, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        State active = ACTIVE.get();
        if (active != state) {
            throw new IllegalStateException("SQLite connection scope must close on its owning thread");
        }
        ACTIVE.remove();
        try {
            state.physical.close();
        } catch (SQLException failure) {
            throw new IllegalStateException("cannot close SQLite operation scope", failure);
        }
    }

    private static final class State {
        private final Path databasePath;
        private final int busyTimeoutMillis;
        private final Connection physical;
        private final AtomicInteger borrows = new AtomicInteger();

        private State(Path databasePath, int busyTimeoutMillis, Connection physical) {
            this.databasePath = databasePath;
            this.busyTimeoutMillis = busyTimeoutMillis;
            this.physical = physical;
        }
    }
}
