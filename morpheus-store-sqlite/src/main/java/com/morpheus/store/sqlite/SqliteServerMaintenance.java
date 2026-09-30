package com.morpheus.store.sqlite;

import com.morpheus.application.operability.StartupOwnership;
import com.morpheus.application.security.LocalWritePermissionHardener;
import com.morpheus.application.store.KnowledgeStoreException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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

        ServerLease(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            IOException lockFailure = null;
            try {
                lock.release();
            } catch (IOException failure) {
                lockFailure = failure;
            }
            try {
                channel.close();
            } catch (IOException failure) {
                if (lockFailure != null) {
                    failure.addSuppressed(lockFailure);
                }
                throw new KnowledgeStoreException("Cannot release the MORPHEUS server lease", failure);
            }
            closed = true;
        }
    }

    public ServerLease acquireServerLease(Path databasePath) {
        Path lockPath = lockPath(databasePath);
        try (StartupOwnership owned = new StartupOwnership()) {
            Path parent = lockPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            rejectUnsafeEntry(lockPath, false, "server lease");
            FileChannel channel = owned.keep(
                    FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE),
                    SqliteServerMaintenance::closeQuietly);
            LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
            if (parent != null) hardener.hardenDirectory(parent);
            hardener.hardenFile(lockPath);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException busy) {
                throw new IllegalStateException("MORPHEUS server lease is already held for this database", busy);
            }
            if (lock == null) {
                throw new IllegalStateException("MORPHEUS server lease is already held for this database");
            }
            ServerLease lease = new ServerLease(channel, lock);
            owned.transferred();
            return lease;
        } catch (IOException failure) {
            throw new KnowledgeStoreException("Cannot acquire MORPHEUS server lease", failure);
        }
    }

    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // The acquisition failure is what the caller needs; the descriptor is released either way.
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
                 PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
                statement.setString(1, target.toString());
                statement.execute();
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
        return restoreOffline(backupPath, databasePath, confirmed, Files::move);
    }

    BackupVerification restoreOffline(
            Path backupPath,
            Path databasePath,
            boolean confirmed,
            SqliteAtomicFileReplacer.MoveOperation replacement) {
        if (!confirmed) {
            throw new IllegalArgumentException("offline restore requires explicit confirmation");
        }
        BackupVerification source = verify(backupPath);
        Path database = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        Path parent = database.getParent();
        if (parent == null) throw new IllegalArgumentException("database path must have a parent directory");
        BackupVerification restored;
        try (ServerLease ignored = acquireServerLease(database);
             SqliteDatabaseLease.Lease databaseLease = SqliteDatabaseLease.acquireExclusive(database)) {
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
                Quarantine quarantine = Quarantine.capture(database);
                try {
                    SqliteAtomicFileReplacer.replace(temp, database, replacement);
                } catch (IOException | RuntimeException failure) {
                    quarantine.rollBack(failure);
                    throw failure;
                }
                quarantine.discard();
                LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
                hardener.hardenDirectory(parent);
                hardener.hardenFile(database);
                // Verification belongs inside the exclusive lease: outside it, the database it reports on is one
                // any other operation is already free to open and change.
                restored = verify(database);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException failure) {
            throw new KnowledgeStoreException("Cannot restore SQLite server backup", failure);
        }
        return restored;
    }

    /**
     * The previous database and its sidecars, moved aside together under names SQLite does not look for.
     *
     * <p>A journal only means anything beside the database it describes, under the exact name derived from it.
     * That leaves no order in which deleting it is safe. Deleting it first -- which is what this did -- discards
     * the only thing that can still recover the previous database, so a replacement that then fails leaves an
     * operator holding a database and no way back. Replacing first and deleting after is worse: in {@code
     * PERSIST} mode a journal survives its transaction and is replayed when its header says it is hot, so the
     * restored database would sit, however briefly, beside the previous database's journal under the name SQLite
     * looks for -- and a delete that failed would feed the old database's pages into the new one.</p>
     *
     * <p>So neither file is deleted while the other is exposed. Both move out of the way in one gesture, and both
     * come back if the replacement fails.</p>
     */
    private static final class Quarantine {
        private static final List<String> SIDECAR_SUFFIXES = List.of("-journal", "-wal", "-shm");

        private final List<QuarantinedEntry> entries;

        private Quarantine(List<QuarantinedEntry> entries) {
            this.entries = entries;
        }

        /**
         * Moves the database out of the way before its sidecars, so that a process that dies midway leaves no
         * database at the target path rather than one stripped of its journal.
         */
        static Quarantine capture(Path database) throws IOException {
            String prefix = database.getFileName() + ".pre-restore-" + BACKUP_TIME.format(Instant.now())
                    + "-" + UUID.randomUUID().toString().substring(0, 8);
            List<QuarantinedEntry> moved = new ArrayList<>();
            try {
                moveAside(database, database.resolveSibling(prefix), moved);
                for (String suffix : SIDECAR_SUFFIXES) {
                    moveAside(sidecar(database, suffix), database.resolveSibling(prefix + suffix), moved);
                }
            } catch (IOException | RuntimeException failure) {
                rollBack(moved, failure);
                throw failure;
            }
            return new Quarantine(List.copyOf(moved));
        }

        void rollBack(Throwable replacementFailure) {
            rollBack(entries, replacementFailure);
        }

        void discard() throws IOException {
            for (QuarantinedEntry entry : entries) {
                Files.deleteIfExists(entry.quarantined());
            }
        }

        private static void moveAside(Path original, Path quarantined, List<QuarantinedEntry> moved)
                throws IOException {
            if (!Files.exists(original, LinkOption.NOFOLLOW_LINKS)) return;
            // Anything already at the quarantine name -- a regular file, a directory, a symbolic link -- is
            // something this restore did not create and must not move, replace or follow.
            if (Files.exists(quarantined, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("restore quarantine destination already exists");
            }
            Files.move(original, quarantined, StandardCopyOption.ATOMIC_MOVE);
            moved.add(new QuarantinedEntry(original, quarantined));
        }

        /**
         * Puts every captured file back under its original name. A rollback move that fails is attached to the
         * failure being reported rather than replacing it: what the caller needs first is why the restore failed.
         */
        private static void rollBack(List<QuarantinedEntry> entries, Throwable replacementFailure) {
            for (QuarantinedEntry entry : entries) {
                try {
                    Files.move(entry.quarantined(), entry.original(), StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException | RuntimeException rollbackFailure) {
                    replacementFailure.addSuppressed(rollbackFailure);
                }
            }
        }

        private record QuarantinedEntry(Path original, Path quarantined) {
        }
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

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 must be available", failure);
        }
    }
}
