package com.morpheus.store.sqlite;

import com.morpheus.application.operability.StartupOwnership;
import com.morpheus.application.store.KnowledgeStoreException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Opens the connection a SQLite store owns, and migrates it under explicit ownership.
 *
 * <p>Every store used to write this out itself: open, migrate, and on failure {@code closeQuietly(opened)} from
 * a {@code catch (SQLException | RuntimeException)}. An {@link Error} raised while migrating -- an
 * {@link ExceptionInInitializerError} from a static initializer, a {@link LinkageError} from a mismatched
 * driver -- walked straight past that catch, so the connection and the shared database lease behind it stayed
 * held with nobody left to release them: the constructor never returns, so the object that would have closed
 * them was never built.</p>
 *
 * <p>The connection is registered with {@link StartupOwnership} the moment it exists. Anything that fails
 * afterwards releases it, whatever its type, and the failure that caused it keeps propagating with the release
 * failure attached as suppressed rather than substituted.</p>
 */
final class SqliteStoreConnection {

    private SqliteStoreConnection() {
    }

    /** A store-specific step that must run on the connection before the schema is migrated. */
    @FunctionalInterface
    interface Preparation {
        void apply(Connection connection) throws SQLException;
    }

    /**
     * @param initializationFailureMessage names the store in the exception a checked failure is wrapped in
     */
    static Connection openAndMigrate(Path databasePath, String initializationFailureMessage) {
        return openAndMigrate(databasePath, initializationFailureMessage, connection -> { });
    }

    static Connection openAndMigrate(
            Path databasePath, String initializationFailureMessage, Preparation prepare) {
        Objects.requireNonNull(prepare, "prepare");
        return open(
                databasePath,
                initializationFailureMessage,
                () -> SqliteDatabaseSecurity.open(databasePath),
                prepare);
    }

    static Connection openAndMigrate(
            Path databasePath, int busyTimeoutMillis, String initializationFailureMessage) {
        if (busyTimeoutMillis <= 0 || busyTimeoutMillis > 60_000) {
            throw new IllegalArgumentException("busyTimeoutMillis must be between 1 and 60000");
        }
        return open(
                databasePath,
                initializationFailureMessage,
                () -> SqliteDatabaseSecurity.open(databasePath, busyTimeoutMillis),
                connection -> { });
    }

    private static Connection open(
            Path databasePath,
            String initializationFailureMessage,
            Opening opening,
            Preparation prepare) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(initializationFailureMessage, "initializationFailureMessage");
        try (StartupOwnership owned = new StartupOwnership()) {
            Connection connection = owned.keep(opening.open(), SqliteStoreConnection::release);
            prepare.apply(connection);
            new SqliteSchemaManager().migrate(connection);
            owned.transferred();
            return connection;
        } catch (KnowledgeStoreException failure) {
            throw failure;
        } catch (SQLException | RuntimeException failure) {
            throw new KnowledgeStoreException(initializationFailureMessage, failure);
        }
    }

    @FunctionalInterface
    private interface Opening {
        Connection open() throws SQLException;
    }

    /**
     * {@link Connection#close()} is declared to throw, and {@link StartupOwnership} releases through a consumer.
     * Converting here keeps the release failure reportable: it reaches the caller as a suppressed exception on
     * the initialization failure, which stays primary.
     */
    private static void release(Connection connection) {
        try {
            connection.close();
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot release the SQLite connection opened for initialization", failure);
        }
    }
}
