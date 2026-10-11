package com.morpheus.store.sqlite;

import com.morpheus.application.security.LocalWritePermissionHardener;
import com.morpheus.application.store.KnowledgeStoreException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Keeps a copy of an existing database as it was, before the schema it records is advanced.
 *
 * <p>The migration is atomic and cannot leave a database half migrated. What it cannot do is go back: once the ledger
 * is ahead, the only way to return to an earlier program is a copy taken before the advance, and an operator who did
 * not take one by hand has none. The published 1.2.0 does not even refuse a database migrated by a later version, so
 * "the old program will tell me" is not a safety net either.</p>
 *
 * <p>One copy is written per attempt, under a name no other attempt can hold. A fixed name per original version was
 * rejected: after an offline restore, or a return to an earlier program, the database at version N is no longer the
 * one an existing copy "of version N" was taken from, and reusing that copy would hand the operator the wrong way
 * back. A copy never replaces a file, because {@code VACUUM INTO} refuses an existing target.</p>
 *
 * <p>The copy is checked for integrity and for the version it is named after, not for an accepted ledger: a
 * divergent history is the migration's refusal to give, under its own message.</p>
 */
final class SqlitePreMigrationCopy {
    static final String REFUSAL = "PRE_MIGRATION_BACKUP_FAILED";

    private static final DateTimeFormatter COPY_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final Supplier<String> uniqueSuffix;

    SqlitePreMigrationCopy() {
        this(() -> COPY_TIME.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    SqlitePreMigrationCopy(Supplier<String> uniqueSuffix) {
        this.uniqueSuffix = Objects.requireNonNull(uniqueSuffix, "uniqueSuffix");
    }

    void keepIfTheSchemaWillAdvance(Connection connection, Path databasePath) {
        int original = recordedVersion(connection);
        if (original > 0 && original < SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION) {
            keep(connection, databasePath, original);
        }
    }

    /**
     * @param original the version read before the copy; read outside any transaction, so another process may have
     *                 migrated the database since, in which case the copy is not a pre-migration copy and is discarded
     */
    void keep(Connection connection, Path databasePath, int original) {
        Path copy = Path.of(databasePath.toAbsolutePath().normalize()
                + String.format(".pre-migration-v%03d-%s.db", original, uniqueSuffix.get()));
        boolean written = false;
        try {
            try (PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
                statement.setString(1, copy.toString());
                statement.execute();
            }
            written = true;
            new LocalWritePermissionHardener().hardenFile(copy);
            if (verifiedVersion(copy) != original) {
                Files.delete(copy);
            }
        } catch (SQLException | IOException | RuntimeException failure) {
            if (written) {
                discard(copy, failure);
            }
            throw new KnowledgeStoreException(
                    REFUSAL + ": cannot keep a copy of the database at schema version " + original
                            + " before migrating it to " + SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION,
                    failure);
        }
    }

    private static int recordedVersion(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet ledger = statement.executeQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'schema_migrations'")) {
                if (!ledger.next()) {
                    return 0;
                }
            }
            return highestRecordedVersion(statement);
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot read SQLite schema version", failure);
        }
    }

    private static int verifiedVersion(Path copy) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + copy);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA query_only = ON");
            try (ResultSet integrity = statement.executeQuery("PRAGMA integrity_check")) {
                integrity.next();
                if (!"ok".equalsIgnoreCase(integrity.getString(1))) {
                    throw new SQLException("SQLite pre-migration copy integrity_check failed");
                }
            }
            return highestRecordedVersion(statement);
        }
    }

    /** An aggregate without GROUP BY always returns one row. */
    private static int highestRecordedVersion(Statement statement) throws SQLException {
        try (ResultSet version = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
            version.next();
            return version.getInt(1);
        }
    }

    private static void discard(Path copy, Exception primary) {
        try {
            Files.deleteIfExists(copy);
        } catch (IOException | RuntimeException cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        }
    }
}
