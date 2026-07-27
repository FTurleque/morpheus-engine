package com.morpheus.store.sqlite;

import com.morpheus.application.composition.CompositionCandidate;
import com.morpheus.application.composition.CompositionConflict;
import com.morpheus.application.composition.CompositionEntityType;
import com.morpheus.application.composition.CompositionProviderState;
import com.morpheus.application.composition.CompositionResolution;
import com.morpheus.application.composition.CompositionSnapshotState;
import com.morpheus.application.composition.CompositionStateStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** SQLite V012 persistence of provider participation, provenance references and explicit composition conflicts. */
public final class SqliteCompositionStateStore implements CompositionStateStore, AutoCloseable {
    private final Connection connection;
    private boolean closed;

    public SqliteCompositionStateStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Connection opened = null;
        try {
            opened = SqliteDatabaseSecurity.open(databasePath);
            configure(opened);
            new SqliteSchemaManager().migrate(opened);
            connection = opened;
        } catch (SQLException | RuntimeException exception) {
            closeQuietly(opened);
            if (exception instanceof KnowledgeStoreException knowledgeStoreException) {
                throw knowledgeStoreException;
            }
            throw new KnowledgeStoreException("Cannot initialize SQLite composition state store", exception);
        }
    }

    @Override
    public synchronized void save(CompositionSnapshotState state) {
        ensureOpen();
        Objects.requireNonNull(state, "state");
        final boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot inspect SQLite auto-commit mode", exception);
        }
        try {
            connection.setAutoCommit(false);
            deleteExisting(state.snapshotId());
            insertSnapshot(state);
            insertProviders(state);
            insertConflicts(state);
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            rollbackQuietly();
            throw exception instanceof KnowledgeStoreException knowledgeStoreException
                    ? knowledgeStoreException
                    : new KnowledgeStoreException("Cannot save composition state for " + state.snapshotId(), exception);
        } finally {
            restoreAutoCommit(previousAutoCommit);
        }
    }

    @Override
    public synchronized Optional<CompositionSnapshotState> find(KnowledgeSnapshotId snapshotId) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT primary_provider_id FROM composition_snapshot_state WHERE snapshot_id = ?")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                ProviderId primary = new ProviderId(result.getString("primary_provider_id"));
                return Optional.of(new CompositionSnapshotState(
                        snapshotId,
                        primary,
                        readProviders(snapshotId),
                        readConflicts(snapshotId)));
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read composition state for " + snapshotId, exception);
        }
    }

    private void deleteExisting(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM composition_snapshot_state WHERE snapshot_id = ?")) {
            statement.setString(1, snapshotId.toString());
            statement.executeUpdate();
        }
    }

    private void insertSnapshot(CompositionSnapshotState state) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO composition_snapshot_state(snapshot_id, primary_provider_id) VALUES (?, ?)")) {
            statement.setString(1, state.snapshotId().toString());
            statement.setString(2, state.primaryProviderId().value());
            statement.executeUpdate();
        }
    }

    private void insertProviders(CompositionSnapshotState state) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO composition_provider_state(
                    snapshot_id, provider_id, priority, required, available, diagnostic_count)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            for (CompositionProviderState provider : state.providers()) {
                statement.setString(1, state.snapshotId().toString());
                statement.setString(2, provider.providerId().value());
                statement.setInt(3, provider.priority());
                statement.setInt(4, provider.required() ? 1 : 0);
                statement.setInt(5, provider.available() ? 1 : 0);
                statement.setInt(6, provider.diagnosticCount());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertConflicts(CompositionSnapshotState state) throws SQLException {
        for (CompositionConflict conflict : state.conflicts()) {
            long conflictId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO composition_conflict(
                        snapshot_id, entity_type, logical_key, field_name, resolution, selected_provider_id, reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, state.snapshotId().toString());
                statement.setString(2, conflict.entityType().name());
                statement.setString(3, conflict.logicalKey());
                statement.setString(4, conflict.field());
                statement.setString(5, conflict.resolution().name());
                if (conflict.selectedProviderId().isPresent()) {
                    statement.setString(6, conflict.selectedProviderId().orElseThrow().value());
                } else {
                    statement.setNull(6, java.sql.Types.VARCHAR);
                }
                statement.setString(7, conflict.reason());
                statement.executeUpdate();
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT last_insert_rowid()")) {
                if (!result.next()) {
                    throw new SQLException("SQLite did not return composition conflict id");
                }
                conflictId = result.getLong(1);
            }
            insertCandidates(conflictId, conflict.candidates());
        }
    }

    private void insertCandidates(long conflictId, List<CompositionCandidate> candidates) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO composition_conflict_candidate(
                    conflict_id, candidate_order, provider_id, priority, candidate_value, source_locator, evidence_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (int index = 0; index < candidates.size(); index++) {
                CompositionCandidate candidate = candidates.get(index);
                statement.setLong(1, conflictId);
                statement.setInt(2, index);
                statement.setString(3, candidate.providerId().value());
                statement.setInt(4, candidate.priority());
                statement.setString(5, candidate.value());
                statement.setString(6, candidate.source());
                statement.setString(7, candidate.evidenceId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<CompositionProviderState> readProviders(KnowledgeSnapshotId snapshotId) throws SQLException {
        List<CompositionProviderState> providers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT provider_id, priority, required, available, diagnostic_count
                FROM composition_provider_state
                WHERE snapshot_id = ?
                ORDER BY priority DESC, provider_id ASC
                """)) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    providers.add(new CompositionProviderState(
                            new ProviderId(result.getString("provider_id")),
                            result.getInt("priority"),
                            result.getInt("required") != 0,
                            result.getInt("available") != 0,
                            result.getInt("diagnostic_count")));
                }
            }
        }
        return List.copyOf(providers);
    }

    private List<CompositionConflict> readConflicts(KnowledgeSnapshotId snapshotId) throws SQLException {
        List<CompositionConflict> conflicts = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT conflict_id, entity_type, logical_key, field_name, resolution, selected_provider_id, reason
                FROM composition_conflict
                WHERE snapshot_id = ?
                ORDER BY entity_type, logical_key, field_name
                """)) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String selected = result.getString("selected_provider_id");
                    conflicts.add(new CompositionConflict(
                            CompositionEntityType.valueOf(result.getString("entity_type")),
                            result.getString("logical_key"),
                            result.getString("field_name"),
                            readCandidates(result.getLong("conflict_id")),
                            CompositionResolution.valueOf(result.getString("resolution")),
                            Optional.ofNullable(selected).map(ProviderId::new),
                            result.getString("reason")));
                }
            }
        }
        return List.copyOf(conflicts);
    }

    private List<CompositionCandidate> readCandidates(long conflictId) throws SQLException {
        List<CompositionCandidate> candidates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT provider_id, priority, candidate_value, source_locator, evidence_id
                FROM composition_conflict_candidate
                WHERE conflict_id = ?
                ORDER BY candidate_order
                """)) {
            statement.setLong(1, conflictId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    candidates.add(new CompositionCandidate(
                            new ProviderId(result.getString("provider_id")),
                            result.getInt("priority"),
                            result.getString("candidate_value"),
                            result.getString("source_locator"),
                            result.getString("evidence_id")));
                }
            }
        }
        return List.copyOf(candidates);
    }

    private void configure(Connection opened) throws SQLException {
        try (Statement statement = opened.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SQLite composition state store is closed");
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve original failure.
        }
    }

    private void restoreAutoCommit(boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot restore SQLite auto-commit mode after composition save", exception);
        }
    }

    private void closeQuietly(Connection opened) {
        if (opened == null) {
            return;
        }
        try {
            opened.close();
        } catch (SQLException ignored) {
            // Preserve original initialization failure.
        }
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
            throw new KnowledgeStoreException("Cannot close SQLite composition state store", exception);
        }
    }
}
