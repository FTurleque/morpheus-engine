package com.morpheus.store.sqlite;

import com.morpheus.application.security.LocalWritePermissionHardener;
import com.morpheus.application.store.KnowledgeStoreException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** M26 SQLite backup, verification and explicitly-offline restore support. */
public final class SqliteServerMaintenance {
    public static final int SUPPORTED_SCHEMA_VERSION = SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION;
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    public record BackupVerification(
            Path path,
            long bytes,
            String sha256,
            int schemaVersion,
            boolean integrityOk) {
        public BackupVerification {
            path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
            if (bytes <= 0) throw new IllegalArgumentException("backup bytes must be positive");
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("sha256 must be lowercase SHA-256 hex");
            }
            if (schemaVersion <= 0) throw new IllegalArgumentException("schemaVersion must be positive");
        }
    }

    /** Lifetime lease used by remote server mode to make offline restore fail closed while the server is active. */
    public static final class ServerLease implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;
        private boolean closed;

        private ServerLease(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            try {
                lock.release();
            } catch (IOException ignored) {
                // Closing the channel below also releases the process lock.
            }
            try {
                channel.close();
            } catch (IOException ignored) {
                // Best effort during server shutdown.
            }
        }
    }

    public ServerLease acquireServerLease(Path databasePath) {
        Path lockPath = lockPath(databasePath);
        try {
            Path parent = lockPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            rejectUnsafeEntry(lockPath, false, "server lease");
            FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
            if (parent != null) hardener.hardenDirectory(parent);
            hardener.hardenFile(lockPath);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException busy) {
                channel.close();
                throw new IllegalStateException("MORPHEUS server lease is already held for this database", busy);
            }
            if (lock == null) {
                channel.close();
                throw new IllegalStateException("MORPHEUS server lease is already held for this database");
            }
            return new ServerLease(channel, lock);
        } catch (IOException failure) {
            throw new KnowledgeStoreException("Cannot acquire MORPHEUS server lease", failure);
        }
    }

    public BackupVerification createBackup(Path databasePath, Path backupDirectory) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(backupDirectory, "backupDirectory");
        Path db = databasePath.toAbsolutePath().normalize();
        Path directory = backupDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            rejectUnsafeEntry(directory, true, "backup directory");
            new LocalWritePermissionHardener().hardenDirectory(directory);
            try (SqliteSpecificationKnowledgeStore ignored = new SqliteSpecificationKnowledgeStore(db)) {
                // Ensure the live database has the current application schema before copying it.
            }
            String filename = "morpheus-" + BACKUP_TIME.format(Instant.now()) + "-"
                    + UUID.randomUUID().toString().substring(0, 8) + ".db";
            Path target = directory.resolve(filename).normalize();
            if (!target.getParent().equals(directory)) {
                throw new IllegalArgumentException("backup path escaped configured directory");
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("backup destination already exists");
            }
            try (Connection connection = SqliteDatabaseSecurity.open(db);
                 Statement statement = connection.createStatement()) {
                statement.execute("VACUUM INTO '" + sqlLiteral(target.toString()) + "'");
            }
            new LocalWritePermissionHardener().hardenFile(target);
            return verify(target);
        } catch (SQLException | IOException failure) {
            throw new KnowledgeStoreException("Cannot create SQLite server backup", failure);
        }
    }

    public BackupVerification verify(Path backupPath) {
        Path backup = requireRegularFile(backupPath, "backup");
        try {
            long bytes = Files.size(backup);
            if (bytes <= 0) throw new IllegalArgumentException("backup file is empty");
            int version;
            boolean integrity;
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backup);
                 Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA query_only = ON");
                try (ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
                    integrity = result.next() && "ok".equalsIgnoreCase(result.getString(1));
                }
                if (!integrity) {
                    throw new IllegalArgumentException("SQLite backup integrity_check failed");
                }
                try (ResultSet result = statement.executeQuery(
                        "SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
                    version = result.next() ? result.getInt(1) : 0;
                }
            }
            if (version <= 0) {
                throw new IllegalArgumentException("backup does not contain a MORPHEUS migration ledger");
            }
            if (version > SUPPORTED_SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                        "backup schema version " + version + " is newer than supported " + SUPPORTED_SCHEMA_VERSION);
            }
            return new BackupVerification(backup, bytes, sha256(backup), version, true);
        } catch (SQLException | IOException failure) {
            if (failure instanceof IllegalArgumentException illegal) throw illegal;
            throw new KnowledgeStoreException("Cannot verify SQLite server backup", failure);
        }
    }

    public BackupVerification restoreOffline(Path backupPath, Path databasePath, boolean confirmed) {
        if (!confirmed) {
            throw new IllegalArgumentException("offline restore requires explicit confirmation");
        }
        BackupVerification source = verify(backupPath);
        Path database = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        Path parent = database.getParent();
        if (parent == null) throw new IllegalArgumentException("database path must have a parent directory");
        try (ServerLease ignored = acquireServerLease(database)) {
            Files.createDirectories(parent);
            rejectUnsafeEntry(database, false, "database");
            rejectUnsafeEntry(sidecar(database, "-journal"), false, "SQLite journal");
            rejectUnsafeEntry(sidecar(database, "-wal"), false, "SQLite WAL");
            rejectUnsafeEntry(sidecar(database, "-shm"), false, "SQLite SHM");
            Path temp = Files.createTempFile(parent, ".morpheus-restore-", ".db");
            try {
                Files.copy(source.path(), temp, StandardCopyOption.REPLACE_EXISTING);
                BackupVerification staged = verify(temp);
                if (!staged.sha256().equals(source.sha256())) {
                    throw new IllegalStateException("restored staging copy checksum mismatch");
                }
                Files.deleteIfExists(sidecar(database, "-journal"));
                Files.deleteIfExists(sidecar(database, "-wal"));
                Files.deleteIfExists(sidecar(database, "-shm"));
                moveReplacing(temp, database);
                LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
                hardener.hardenDirectory(parent);
                hardener.hardenFile(database);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException failure) {
            throw new KnowledgeStoreException("Cannot restore SQLite server backup", failure);
        }
        return verify(database);
    }

    private static Path requireRegularFile(Path path, String label) {
        Objects.requireNonNull(path, label);
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw new IllegalArgumentException(label + " must be a regular non-symbolic file");
        }
        return normalized;
    }

    private static void rejectUnsafeEntry(Path path, boolean requireDirectory, String label) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException(label + " must not be a symbolic link");
        }
        if (requireDirectory && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(label + " must be a directory");
        }
        if (!requireDirectory && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(label + " must be a regular file");
        }
    }

    private static Path lockPath(Path databasePath) {
        Path db = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        return db.resolveSibling(db.getFileName() + ".server.lock");
    }

    private static Path sidecar(Path database, String suffix) {
        return database.resolveSibling(database.getFileName() + suffix);
    }

    private static String sqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
