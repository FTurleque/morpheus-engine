package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SyncStateStore;
import com.morpheus.application.sync.ProjectSyncState;
import com.morpheus.application.sync.SourceArchiveRecord;
import com.morpheus.application.sync.SourceFingerprint;
import com.morpheus.application.sync.SourceInventory;
import com.morpheus.application.sync.SourcePath;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.application.sync.SyncStateConflictException;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** SQLite adapter for M7 synchronization state, current inventory and immutable source archives. */
public final class SqliteSyncStateStore implements SyncStateStore, AutoCloseable {
    private final Connection connection;
    private boolean closed;

    public SqliteSyncStateStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        this.connection = SqliteStoreConnection.openAndMigrate(
                databasePath, "Cannot initialize SQLite synchronization state store", SqliteSyncStateStore::configure);
    }

    @Override
    public synchronized Optional<ProjectSyncState> findSyncState(ProjectSpecificationId projectId) {
        ensureOpen();
        Objects.requireNonNull(projectId, "projectId");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT last_attempt_at, last_successful_sync_at, last_observed_change_at,
                       source_revision, last_successful_mode, pending_full_rebuild_reason,
                       current_source_count, revision
                FROM sync_state
                WHERE project_id = ?
                """)) {
            statement.setString(1, projectId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ProjectSyncState(
                        projectId,
                        instant(result.getString("last_attempt_at")),
                        instant(result.getString("last_successful_sync_at")),
                        instant(result.getString("last_observed_change_at")),
                        optional(result.getString("source_revision")),
                        optional(result.getString("last_successful_mode")).map(SyncPlan.SyncMode::valueOf),
                        optional(result.getString("pending_full_rebuild_reason")).map(SyncPlan.FullRebuildReason::valueOf),
                        result.getInt("current_source_count"),
                        result.getLong("revision")));
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read synchronization state for " + projectId, exception);
        }
    }

    @Override
    public synchronized Optional<SourceInventory> findCurrentInventory(ProjectSpecificationId projectId) {
        ensureOpen();
        Objects.requireNonNull(projectId, "projectId");
        try (PreparedStatement state = connection.prepareStatement(
                "SELECT source_revision, inventory_captured_at FROM sync_state WHERE project_id = ?")) {
            state.setString(1, projectId.toString());
            try (ResultSet result = state.executeQuery()) {
                if (!result.next() || result.getString("inventory_captured_at") == null) {
                    return Optional.empty();
                }
                Optional<String> revision = optional(result.getString("source_revision"));
                Instant capturedAt = Instant.parse(result.getString("inventory_captured_at"));
                return Optional.of(new SourceInventory(projectId, revision, capturedAt, readEntries(projectId)));
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read source inventory for " + projectId, exception);
        }
    }

    @Override
    public synchronized List<SourceArchiveRecord> listArchives(ProjectSpecificationId projectId) {
        ensureOpen();
        Objects.requireNonNull(projectId, "projectId");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT source_path, fingerprint_sha256, size_bytes, archived_at, reason, moved_to_path, source_revision
                FROM sync_source_archives
                WHERE project_id = ?
                ORDER BY archived_at, source_path, reason
                """)) {
            statement.setString(1, projectId.toString());
            List<SourceArchiveRecord> records = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(new SourceArchiveRecord(
                            projectId,
                            new SourceInventory.Entry(
                                    new SourcePath(result.getString("source_path")),
                                    new SourceFingerprint(result.getString("fingerprint_sha256")),
                                    result.getLong("size_bytes")),
                            Instant.parse(result.getString("archived_at")),
                            SourceArchiveRecord.ArchiveReason.valueOf(result.getString("reason")),
                            optional(result.getString("moved_to_path")).map(SourcePath::new),
                            optional(result.getString("source_revision"))));
                }
            }
            return records.stream().sorted().toList();
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read source archives for " + projectId, exception);
        }
    }

    @Override
    public synchronized long recordAttempt(
            ProjectSpecificationId projectId,
            long expectedRevision,
            Instant attemptedAt,
            Optional<SyncPlan.FullRebuildReason> pendingFullRebuildReason) {
        ensureOpen();
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        Objects.requireNonNull(pendingFullRebuildReason, "pendingFullRebuildReason");
        requireProject(projectId);
        try {
            int changed;
            if (expectedRevision == 0) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO sync_state(project_id, last_attempt_at, pending_full_rebuild_reason,
                                               current_source_count, revision)
                        VALUES (?, ?, ?, 0, 1)
                        ON CONFLICT(project_id) DO NOTHING
                        """)) {
                    statement.setString(1, projectId.toString());
                    statement.setString(2, attemptedAt.toString());
                    nullable(statement, 3, pendingFullRebuildReason.map(Enum::name));
                    changed = statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE sync_state
                        SET last_attempt_at = ?, pending_full_rebuild_reason = ?, revision = revision + 1
                        WHERE project_id = ? AND revision = ?
                        """)) {
                    statement.setString(1, attemptedAt.toString());
                    nullable(statement, 2, pendingFullRebuildReason.map(Enum::name));
                    statement.setString(3, projectId.toString());
                    statement.setLong(4, expectedRevision);
                    changed = statement.executeUpdate();
                }
            }
            if (changed != 1) {
                throw conflict(projectId, expectedRevision);
            }
            return expectedRevision + 1;
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot record synchronization attempt for " + projectId, exception);
        }
    }

    @Override
    public synchronized long commitSuccessfulSync(
            SourceInventory inventory,
            long expectedRevision,
            SyncPlan.SyncMode mode,
            Instant attemptedAt,
            Instant completedAt,
            Optional<Instant> lastObservedChangeAt,
            List<SourceArchiveRecord> newArchives) {
        ensureOpen();
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(lastObservedChangeAt, "lastObservedChangeAt");
        Objects.requireNonNull(newArchives, "newArchives");
        if (completedAt.isBefore(attemptedAt)) {
            throw new IllegalArgumentException("completedAt must not be before attemptedAt");
        }
        lastObservedChangeAt.ifPresent(value -> {
            if (value.isAfter(completedAt)) {
                throw new IllegalArgumentException("lastObservedChangeAt must not be after completedAt");
            }
        });
        newArchives.forEach(record -> {
            if (!record.projectId().equals(inventory.projectId())) {
                throw new IllegalArgumentException("archive belongs to another project");
            }
        });
        requireProject(inventory.projectId());

        SqliteTransactionRunner.runVoid(connection,
                "Cannot commit synchronization state for " + inventory.projectId(), ignored -> {
                upsertSuccessfulState(inventory, expectedRevision, mode, attemptedAt, completedAt, lastObservedChangeAt);
                replaceInventory(inventory);
                insertArchives(newArchives);
        });
        return expectedRevision + 1;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            connection.close();
            closed = true;
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot close SQLite synchronization state store", exception);
        }
    }

    private void upsertSuccessfulState(
            SourceInventory inventory,
            long expectedRevision,
            SyncPlan.SyncMode mode,
            Instant attemptedAt,
            Instant completedAt,
            Optional<Instant> lastObservedChangeAt) throws SQLException {
        int changed;
        if (expectedRevision == 0) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sync_state(
                        project_id, last_attempt_at, last_successful_sync_at, last_observed_change_at,
                        source_revision, last_successful_mode, pending_full_rebuild_reason,
                        inventory_captured_at, current_source_count, revision)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, 1)
                    ON CONFLICT(project_id) DO NOTHING
                    """)) {
                bindSuccessful(statement, inventory, mode, attemptedAt, completedAt, lastObservedChangeAt);
                changed = statement.executeUpdate();
            }
        } else {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE sync_state SET
                        last_attempt_at = ?,
                        last_successful_sync_at = ?,
                        last_observed_change_at = ?,
                        source_revision = ?,
                        last_successful_mode = ?,
                        pending_full_rebuild_reason = NULL,
                        inventory_captured_at = ?,
                        current_source_count = ?,
                        revision = revision + 1
                    WHERE project_id = ? AND revision = ?
                    """)) {
                statement.setString(1, attemptedAt.toString());
                statement.setString(2, completedAt.toString());
                nullable(statement, 3, lastObservedChangeAt.map(Instant::toString));
                nullable(statement, 4, inventory.sourceRevision());
                statement.setString(5, mode.name());
                statement.setString(6, inventory.capturedAt().toString());
                statement.setInt(7, inventory.entries().size());
                statement.setString(8, inventory.projectId().toString());
                statement.setLong(9, expectedRevision);
                changed = statement.executeUpdate();
            }
        }
        if (changed != 1) {
            throw conflict(inventory.projectId(), expectedRevision);
        }
    }

    private void bindSuccessful(
            PreparedStatement statement,
            SourceInventory inventory,
            SyncPlan.SyncMode mode,
            Instant attemptedAt,
            Instant completedAt,
            Optional<Instant> lastObservedChangeAt) throws SQLException {
        statement.setString(1, inventory.projectId().toString());
        statement.setString(2, attemptedAt.toString());
        statement.setString(3, completedAt.toString());
        nullable(statement, 4, lastObservedChangeAt.map(Instant::toString));
        nullable(statement, 5, inventory.sourceRevision());
        statement.setString(6, mode.name());
        statement.setString(7, inventory.capturedAt().toString());
        statement.setInt(8, inventory.entries().size());
    }

    /** The revision actually persisted, read after a refused write so the failure says what the writer was up against. */
    private SyncStateConflictException conflict(ProjectSpecificationId projectId, long expectedRevision) {
        long current = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT revision FROM sync_state WHERE project_id = ?")) {
            statement.setString(1, projectId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    current = result.getLong(1);
                }
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot read synchronization state revision for " + projectId, failure);
        }
        return new SyncStateConflictException(projectId.toString(), expectedRevision, current);
    }

    private void replaceInventory(SourceInventory inventory) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM sync_inventory_entries WHERE project_id = ?")) {
            delete.setString(1, inventory.projectId().toString());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO sync_inventory_entries(project_id, source_path, fingerprint_sha256, size_bytes)
                VALUES (?, ?, ?, ?)
                """)) {
            for (SourceInventory.Entry entry : inventory.entries()) {
                insert.setString(1, inventory.projectId().toString());
                insert.setString(2, entry.path().toString());
                insert.setString(3, entry.fingerprint().sha256());
                insert.setLong(4, entry.sizeBytes());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void insertArchives(List<SourceArchiveRecord> records) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT OR IGNORE INTO sync_source_archives(
                    project_id, source_path, fingerprint_sha256, size_bytes,
                    archived_at, reason, moved_to_path, source_revision)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (SourceArchiveRecord record : records) {
                insert.setString(1, record.projectId().toString());
                insert.setString(2, record.source().path().toString());
                insert.setString(3, record.source().fingerprint().sha256());
                insert.setLong(4, record.source().sizeBytes());
                insert.setString(5, record.archivedAt().toString());
                insert.setString(6, record.reason().name());
                nullable(insert, 7, record.movedTo().map(SourcePath::toString));
                nullable(insert, 8, record.sourceRevision());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private List<SourceInventory.Entry> readEntries(ProjectSpecificationId projectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT source_path, fingerprint_sha256, size_bytes
                FROM sync_inventory_entries
                WHERE project_id = ?
                ORDER BY source_path
                """)) {
            statement.setString(1, projectId.toString());
            List<SourceInventory.Entry> entries = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new SourceInventory.Entry(
                            new SourcePath(result.getString("source_path")),
                            new SourceFingerprint(result.getString("fingerprint_sha256")),
                            result.getLong("size_bytes")));
                }
            }
            return entries;
        }
    }

    private void requireProject(ProjectSpecificationId projectId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM projects WHERE id = ?")) {
            statement.setString(1, projectId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new KnowledgeStoreException("project not found for synchronization state: " + projectId);
                }
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot validate project for synchronization state: " + projectId, exception);
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new KnowledgeStoreException("SQLite synchronization state store is closed");
        }
    }

    private static Optional<String> optional(String value) {
        return Optional.ofNullable(value);
    }

    private static Optional<Instant> instant(String value) {
        return Optional.ofNullable(value).map(Instant::parse);
    }

    private static void nullable(PreparedStatement statement, int index, Optional<String> value) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

}
