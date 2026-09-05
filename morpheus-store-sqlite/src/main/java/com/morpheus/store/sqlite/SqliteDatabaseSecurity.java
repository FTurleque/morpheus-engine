package com.morpheus.store.sqlite;

import com.morpheus.application.security.LocalWritePermissionHardener;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;

/** Shared secure local SQLite entry point used by every persistent adapter. */
final class SqliteDatabaseSecurity {
    static final int DEFAULT_BUSY_TIMEOUT_MILLIS = 5_000;

    private SqliteDatabaseSecurity() {
    }

    static Connection open(Path databasePath) throws SQLException {
        return open(databasePath, DEFAULT_BUSY_TIMEOUT_MILLIS);
    }

    static Connection open(Path databasePath, int busyTimeoutMillis) throws SQLException {
        Objects.requireNonNull(databasePath, "databasePath");
        Connection scoped = SqliteConnectionScope.borrowIfActive(databasePath, busyTimeoutMillis);
        return scoped != null ? scoped : openPhysical(databasePath, busyTimeoutMillis);
    }

    static Connection openPhysical(Path databasePath, int busyTimeoutMillis) throws SQLException {
        Objects.requireNonNull(databasePath, "databasePath");
        if (busyTimeoutMillis <= 0 || busyTimeoutMillis > 60_000) {
            throw new IllegalArgumentException("busyTimeoutMillis must be between 1 and 60000");
        }

        Path absolutePath = databasePath.toAbsolutePath().normalize();
        rejectUnsafeDatabaseEntry(absolutePath);
        rejectUnsafeSidecarEntry(sidecar(absolutePath, "-wal"));
        rejectUnsafeSidecarEntry(sidecar(absolutePath, "-shm"));
        rejectUnsafeSidecarEntry(sidecar(absolutePath, "-journal"));
        LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            hardener.hardenDirectory(parent);
        }

        SqliteDatabaseLease.Lease lease = SqliteDatabaseLease.acquireShared(absolutePath);
        Connection connection = null;
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + absolutePath);
            hardener.hardenFile(absolutePath);
            configureSecureDefaults(connection, busyTimeoutMillis);
            ensureAndHardenPersistentJournal(connection, absolutePath, hardener);
            return SqliteDatabaseLease.guard(connection, lease);
        } catch (SQLException | RuntimeException | Error failure) {
            SqliteContentionMetrics.connectionOpenFailed(failure);
            unwind(absolutePath, connection, lease, failure);
            throw failure;
        }
    }

    /**
     * Gives back what a failed open acquired, without ever claiming more than it can prove.
     *
     * <p>The lease is what keeps offline maintenance out while a physical handle may be held, so it goes back
     * only once a close has established the handle is gone. When that close fails the handle and its lease are
     * retained together instead: refusing to release them was already right, but leaving them unreachable meant
     * nothing could ever finish the job, and the database stayed reserved for the life of the process.</p>
     *
     * <p>The failure that brought us here stays primary. A cleanup failure is attached to it, never substituted
     * for it -- the caller needs to know why the open failed first.</p>
     */
    // java:S1181 catches Error deliberately: a driver failing on a LinkageError leaves the same unresolved
    // handle as a SQLException, and it must be retained rather than lost.
    @SuppressWarnings("java:S1181")
    private static void unwind(
            Path absolutePath, Connection connection, SqliteDatabaseLease.Lease lease, Throwable primary) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException | RuntimeException | Error closeFailure) {
                SqliteDatabaseLease.retain(absolutePath, connection, lease, closeFailure);
                if (primary != closeFailure) {
                    primary.addSuppressed(closeFailure);
                }
                return;
            }
        }
        lease.close();
    }

    private static void rejectUnsafeSidecarEntry(Path sidecar) {
        if (!Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(sidecar)
                || !Files.isRegularFile(sidecar, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("SQLite sidecar path must be a regular non-symbolic file");
        }
    }

    private static void rejectUnsafeDatabaseEntry(Path databasePath) {
        if (!Files.exists(databasePath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(databasePath)
                || !Files.isRegularFile(databasePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("SQLite database path must be a regular non-symbolic file");
        }
    }

    private static void configureSecureDefaults(Connection connection, int busyTimeoutMillis) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = " + busyTimeoutMillis);
            statement.execute("PRAGMA temp_store = MEMORY");
            statement.execute("PRAGMA locking_mode = NORMAL");
            try (ResultSet result = statement.executeQuery("PRAGMA journal_mode = PERSIST")) {
                if (!result.next() || !"persist".equals(result.getString(1).toLowerCase(Locale.ROOT))) {
                    throw new SQLException("SQLite journal mode must be PERSIST for secure reusable sidecars");
                }
            }
        }
    }

    private static void ensureAndHardenPersistentJournal(
            Connection connection,
            Path databasePath,
            LocalWritePermissionHardener hardener) throws SQLException {
        Path journal = sidecar(databasePath, "-journal");
        if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS morpheus_local_security_probe(id INTEGER PRIMARY KEY)");
                statement.execute("DROP TABLE IF EXISTS morpheus_local_security_probe");
            }
        }
        if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)) {
            throw new SQLException("SQLite persistent journal was not created as a regular file");
        }
        if (Files.exists(sidecar(databasePath, "-wal"), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(sidecar(databasePath, "-shm"), LinkOption.NOFOLLOW_LINKS)) {
            throw new SQLException("SQLite WAL/SHM sidecars must be absent in PERSIST mode");
        }
        hardener.hardenFile(journal);
    }

    private static Path sidecar(Path databasePath, String suffix) {
        return Path.of(databasePath + suffix);
    }
}
