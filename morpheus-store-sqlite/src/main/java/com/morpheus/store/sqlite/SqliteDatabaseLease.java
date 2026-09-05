package com.morpheus.store.sqlite;

import com.morpheus.application.operability.StartupOwnership;
import com.morpheus.application.security.LocalWritePermissionHardener;
import com.morpheus.application.store.KnowledgeStoreException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-process reader/exclusive-maintenance lease for one SQLite database.
 *
 * <p>Normal physical connections share one OS-level shared lock per JVM. Offline maintenance requires an
 * exclusive lock on the same file, so a restore fails closed while any MORPHEUS process still owns a physical
 * connection to the database.</p>
 */
final class SqliteDatabaseLease {
    private static final Object MONITOR = new Object();
    private static final Map<Path, SharedState> SHARED = new HashMap<>();
    private static final Set<Path> EXCLUSIVE = new HashSet<>();
    /**
     * Physical handles whose close failed, with the lease each still holds.
     *
     * <p>Refusing to release a lease over an unproven close is what keeps offline maintenance out, but it left
     * the handle and its lease unreachable: nothing could finish the release, so the database stayed reserved
     * for the life of the process. They are retained here instead, and {@link #resolveRetained(Path)} is the
     * controlled retry. A retry that fails changes nothing -- the lease is still held, and still refused.</p>
     */
    private static final Map<Path, List<RetainedHandle>> RETAINED = new HashMap<>();

    private SqliteDatabaseLease() {
    }

    // java:S1181 catches Error deliberately: the lock channel opened just above must be released even when
    // locking fails on a LinkageError. The failure keeps propagating unchanged.
    @SuppressWarnings("java:S1181")
    static Lease acquireShared(Path databasePath) {
        Path database = normalize(databasePath);
        synchronized (MONITOR) {
            if (EXCLUSIVE.contains(database)) {
                throw new IllegalStateException("SQLite database is reserved for exclusive maintenance");
            }
            SharedState existing = SHARED.get(database);
            if (existing != null) {
                existing.references++;
                return Lease.shared(database);
            }

            Path lockPath = lockPath(database);
            FileChannel channel = openLockChannel(lockPath);
            try {
                FileLock lock = tryLock(channel, true,
                        "SQLite database is reserved for exclusive maintenance");
                SHARED.put(database, new SharedState(channel, lock));
                return Lease.shared(database);
            } catch (RuntimeException | Error failure) {
                closeQuietly(channel);
                throw failure;
            }
        }
    }

    // java:S1181 catches Error deliberately: the lock channel opened just above must be released even when
    // locking fails on a LinkageError. The failure keeps propagating unchanged.
    @SuppressWarnings("java:S1181")
    static Lease acquireExclusive(Path databasePath) {
        Path database = normalize(databasePath);
        synchronized (MONITOR) {
            // A handle whose close failed still holds its lease. Finishing that release is the only way it can
            // ever be given back, so it is attempted here -- and it releases nothing unless a close succeeds.
            resolveRetained(database);
            if (SHARED.containsKey(database) || EXCLUSIVE.contains(database)) {
                throw new IllegalStateException("SQLite database is still open in another MORPHEUS operation");
            }

            Path lockPath = lockPath(database);
            FileChannel channel = openLockChannel(lockPath);
            try {
                FileLock lock = tryLock(channel, false,
                        "SQLite database is still open in another MORPHEUS process");
                EXCLUSIVE.add(database);
                return Lease.exclusive(database, channel, lock);
            } catch (RuntimeException | Error failure) {
                closeQuietly(channel);
                throw failure;
            }
        }
    }

    static Connection guard(Connection connection, Lease lease) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(lease, "lease");
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean released = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                SqliteDatabaseLease.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("close")) {
                        // Closing makes the connection unusable whatever happens next. Releasing the database
                        // lease is a different question, and it is answered only by a driver close that
                        // succeeded: the lease is what makes offline maintenance impossible while a physical
                        // handle is held, so a close whose outcome is unknown must keep it. Releasing it here
                        // regardless let acquireExclusive succeed over a connection that may still be alive,
                        // and a restore may replace the database file and its sidecars underneath one.
                        closed.set(true);
                        if (!released.get()) {
                            connection.close();
                            // Only now is the physical handle proven gone, so the lease may go with it.
                            released.set(true);
                            lease.close();
                        }
                        return null;
                    }
                    if (name.equals("isClosed")) {
                        return closed.get() || connection.isClosed();
                    }
                    if (name.equals("toString")) {
                        return "LeasedSqliteConnection[" + connection + "]";
                    }
                    if (name.equals("hashCode")) return System.identityHashCode(proxy);
                    if (name.equals("equals")) return proxy == args[0];
                    if (closed.get()) throw new SQLException("SQLite connection is closed");
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    /**
     * Opens the lock channel and hands it to the caller only once hardening has succeeded.
     *
     * <p>Hardening runs after the channel exists and refuses fail-closed on a permissive ancestor, which is a
     * {@link com.morpheus.application.security.LocalWritePermissionHardener.LocalWritePermissionException} --
     * unchecked, so the {@code IOException} handler never saw it and the descriptor stayed open for the life of
     * the process. Ownership is explicit until the channel is returned.</p>
     */
    private static FileChannel openLockChannel(Path lockPath) {
        try (StartupOwnership owned = new StartupOwnership()) {
            Path parent = lockPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            rejectUnsafeLockEntry(lockPath);
            FileChannel channel = owned.keep(
                    FileChannel.open(
                            lockPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE),
                    SqliteDatabaseLease::closeQuietly);
            LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
            if (parent != null) hardener.hardenDirectory(parent);
            hardener.hardenFile(lockPath);
            owned.transferred();
            return channel;
        } catch (IOException failure) {
            throw new KnowledgeStoreException("Cannot open SQLite database access lease", failure);
        }
    }

    private static FileLock tryLock(FileChannel channel, boolean shared, String busyMessage) {
        try {
            FileLock lock = channel.tryLock(0L, Long.MAX_VALUE, shared);
            if (lock == null) throw new IllegalStateException(busyMessage);
            return lock;
        } catch (OverlappingFileLockException busy) {
            throw new IllegalStateException(busyMessage, busy);
        } catch (IOException failure) {
            throw new KnowledgeStoreException("Cannot acquire SQLite database access lease", failure);
        }
    }

    /**
     * Keeps a physical handle and its lease together after a close that failed.
     *
     * <p>The lease is deliberately not released: the handle may still be alive, and an offline restore replaces
     * the database file and its sidecars. Keeping both reachable is what separates "not released yet" from
     * "leaked" -- the second cannot be recovered, and this one can.</p>
     */
    static void retain(Path databasePath, Connection connection, Lease lease, Throwable closeFailure) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(closeFailure, "closeFailure");
        Path database = normalize(databasePath);
        synchronized (MONITOR) {
            RETAINED.computeIfAbsent(database, key -> new ArrayList<>())
                    .add(new RetainedHandle(connection, lease, closeFailure));
        }
    }

    /**
     * Tries to finish the release of every handle retained for this database.
     *
     * <p>A handle whose close succeeds gives its lease back and is forgotten. One that fails again is kept, with
     * the newest failure recorded, so the lease stays held and maintenance stays refused.</p>
     */
    // java:S1181 catches Error deliberately: a driver that fails on a LinkageError must leave the handle
    // retained rather than unreachable, exactly as a SQLException does.
    @SuppressWarnings("java:S1181")
    static void resolveRetained(Path databasePath) {
        Path database = normalize(databasePath);
        synchronized (MONITOR) {
            List<RetainedHandle> retained = RETAINED.get(database);
            if (retained == null) {
                return;
            }
            List<RetainedHandle> unresolved = new ArrayList<>();
            for (RetainedHandle handle : retained) {
                try {
                    handle.connection.close();
                } catch (SQLException | RuntimeException | Error failure) {
                    unresolved.add(new RetainedHandle(handle.connection, handle.lease, failure));
                    continue;
                }
                handle.lease.close();
            }
            if (unresolved.isEmpty()) {
                RETAINED.remove(database);
            } else {
                RETAINED.put(database, unresolved);
            }
        }
    }

    /** How many handles are still waiting to be released for this database. */
    static int retainedCount(Path databasePath) {
        Path database = normalize(databasePath);
        synchronized (MONITOR) {
            return RETAINED.getOrDefault(database, List.of()).size();
        }
    }

    /** Why the most recent release attempt for a retained handle failed, for diagnostics and tests. */
    static Optional<Throwable> lastRetainedFailure(Path databasePath) {
        Path database = normalize(databasePath);
        synchronized (MONITOR) {
            List<RetainedHandle> retained = RETAINED.getOrDefault(database, List.of());
            return retained.isEmpty()
                    ? Optional.empty()
                    : Optional.of(retained.get(retained.size() - 1).closeFailure);
        }
    }

    private record RetainedHandle(Connection connection, Lease lease, Throwable closeFailure) {
    }

    private static void releaseShared(Path database) {
        synchronized (MONITOR) {
            SharedState state = SHARED.get(database);
            if (state == null || state.references <= 0) {
                throw new IllegalStateException("SQLite shared lease reference count is inconsistent");
            }
            state.references--;
            if (state.references == 0) {
                SHARED.remove(database);
                release(state.lock, state.channel);
            }
        }
    }

    private static void releaseExclusive(Path database, FileLock lock, FileChannel channel) {
        synchronized (MONITOR) {
            EXCLUSIVE.remove(database);
            release(lock, channel);
        }
    }

    private static void release(FileLock lock, FileChannel channel) {
        try {
            lock.release();
        } catch (IOException ignored) {
            // Closing the channel below also releases the operating-system lock.
        }
        closeQuietly(channel);
    }

    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // Best effort while unwinding lease acquisition or release.
        }
    }

    private static void rejectUnsafeLockEntry(Path lockPath) {
        if (!Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(lockPath) || !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("SQLite database access lease must be a regular non-symbolic file");
        }
    }

    private static Path normalize(Path databasePath) {
        return Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    private static Path lockPath(Path databasePath) {
        return databasePath.resolveSibling(databasePath.getFileName() + ".access.lock");
    }

    static final class Lease implements AutoCloseable {
        private final Path database;
        private final boolean shared;
        private final FileChannel channel;
        private final FileLock lock;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(Path database, boolean shared, FileChannel channel, FileLock lock) {
            this.database = database;
            this.shared = shared;
            this.channel = channel;
            this.lock = lock;
        }

        private static Lease shared(Path database) {
            return new Lease(database, true, null, null);
        }

        private static Lease exclusive(Path database, FileChannel channel, FileLock lock) {
            return new Lease(database, false, channel, lock);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            if (shared) {
                releaseShared(database);
            } else {
                releaseExclusive(database, lock, channel);
            }
        }
    }

    private static final class SharedState {
        private final FileChannel channel;
        private final FileLock lock;
        private int references = 1;

        private SharedState(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }
    }
}
