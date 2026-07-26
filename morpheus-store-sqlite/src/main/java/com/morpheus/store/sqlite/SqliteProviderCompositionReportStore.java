package com.morpheus.store.sqlite;

import com.morpheus.application.composition.ProviderCompositionConflict;
import com.morpheus.application.composition.ProviderCompositionReport;
import com.morpheus.application.composition.ProviderCompositionReportStore;
import com.morpheus.application.composition.ProviderConflictContender;
import com.morpheus.application.composition.ProviderConflictResolution;
import com.morpheus.application.composition.ProviderContribution;
import com.morpheus.application.composition.ProviderContributionStatus;
import com.morpheus.application.composition.ProviderEntityKind;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** SQLite adapter for immutable snapshot-scoped M18 provider composition reports. */
public final class SqliteProviderCompositionReportStore implements ProviderCompositionReportStore, AutoCloseable {
    private final Connection connection;
    private boolean closed;

    public SqliteProviderCompositionReportStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Path absolutePath = databasePath.toAbsolutePath().normalize();
        Connection opened = null;
        try {
            Path parent = absolutePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            opened = DriverManager.getConnection("jdbc:sqlite:" + absolutePath);
            configure(opened);
            new SqliteSchemaManager().migrate(opened);
            this.connection = opened;
        } catch (SQLException | IOException | RuntimeException exception) {
            closeQuietly(opened);
            if (exception instanceof KnowledgeStoreException knowledgeStoreException) {
                throw knowledgeStoreException;
            }
            throw new KnowledgeStoreException("Cannot initialize SQLite provider composition report store", exception);
        }
    }

    @Override
    public synchronized void put(KnowledgeSnapshotId snapshotId, ProviderCompositionReport report) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(report, "report");

        Optional<ProviderCompositionReport> existing = find(snapshotId);
        if (existing.isPresent()) {
            if (!existing.orElseThrow().equals(report)) {
                throw new KnowledgeStoreException("provider composition report collision: " + snapshotId);
            }
            return;
        }
        if (!snapshotExists(snapshotId)) {
            throw new KnowledgeStoreException("snapshot not found: " + snapshotId);
        }

        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot begin provider composition report transaction", exception);
        }

        try {
            insertSummary(snapshotId);
            insertContributions(snapshotId, report.contributions());
            insertConflicts(snapshotId, report.conflicts());
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            rollbackQuietly();
            if (exception instanceof KnowledgeStoreException knowledgeStoreException) {
                throw knowledgeStoreException;
            }
            throw new KnowledgeStoreException("Cannot store provider composition report for " + snapshotId, exception);
        } finally {
            restoreAutoCommit(previousAutoCommit);
        }
    }

    @Override
    public synchronized Optional<ProviderCompositionReport> find(KnowledgeSnapshotId snapshotId) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        try {
            if (!compositionExists(snapshotId)) {
                return Optional.empty();
            }
            return Optional.of(new ProviderCompositionReport(
                    readContributions(snapshotId),
                    readConflicts(snapshotId)));
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read provider composition report for " + snapshotId, exception);
        }
    }

    private void insertSummary(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO snapshot_provider_composition(snapshot_id) VALUES (?)")) {
            statement.setString(1, snapshotId.toString());
            statement.executeUpdate();
        }
    }

    private void insertContributions(KnowledgeSnapshotId snapshotId, List<ProviderContribution> contributions)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_provider_contribution(
                    snapshot_id, provider_id, precedence, required, status, item_count, detail
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (ProviderContribution contribution : contributions) {
                statement.setString(1, snapshotId.toString());
                statement.setString(2, contribution.providerId().value());
                statement.setInt(3, contribution.precedence());
                statement.setInt(4, contribution.required() ? 1 : 0);
                statement.setString(5, contribution.status().name());
                statement.setInt(6, contribution.itemCount());
                statement.setString(7, contribution.detail().orElse(null));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertConflicts(KnowledgeSnapshotId snapshotId, List<ProviderCompositionConflict> conflicts)
            throws SQLException {
        try (PreparedStatement conflictStatement = connection.prepareStatement("""
                INSERT INTO snapshot_provider_conflict(
                    snapshot_id, conflict_ordinal, entity_kind, logical_key, resolution,
                    winner_provider_id, winner_entity_id, winner_precedence, reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement contenderStatement = connection.prepareStatement("""
                INSERT INTO snapshot_provider_conflict_contender(
                    snapshot_id, conflict_ordinal, provider_id, entity_id, precedence
                ) VALUES (?, ?, ?, ?, ?)
                """)) {
            for (int ordinal = 0; ordinal < conflicts.size(); ordinal++) {
                ProviderCompositionConflict conflict = conflicts.get(ordinal);
                conflictStatement.setString(1, snapshotId.toString());
                conflictStatement.setInt(2, ordinal);
                conflictStatement.setString(3, conflict.entityKind().name());
                conflictStatement.setString(4, conflict.logicalKey());
                conflictStatement.setString(5, conflict.resolution().name());
                if (conflict.winner().isPresent()) {
                    ProviderConflictContender winner = conflict.winner().orElseThrow();
                    conflictStatement.setString(6, winner.providerId().value());
                    conflictStatement.setString(7, winner.entityId());
                    conflictStatement.setInt(8, winner.precedence());
                } else {
                    conflictStatement.setString(6, null);
                    conflictStatement.setString(7, null);
                    conflictStatement.setObject(8, null);
                }
                conflictStatement.setString(9, conflict.reason());
                conflictStatement.addBatch();

                for (ProviderConflictContender contender : conflict.contenders()) {
                    contenderStatement.setString(1, snapshotId.toString());
                    contenderStatement.setInt(2, ordinal);
                    contenderStatement.setString(3, contender.providerId().value());
                    contenderStatement.setString(4, contender.entityId());
                    contenderStatement.setInt(5, contender.precedence());
                    contenderStatement.addBatch();
                }
            }
            conflictStatement.executeBatch();
            contenderStatement.executeBatch();
        }
    }

    private List<ProviderContribution> readContributions(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT provider_id, precedence, required, status, item_count, detail
                FROM snapshot_provider_contribution
                WHERE snapshot_id = ?
                ORDER BY precedence DESC, provider_id
                """)) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<ProviderContribution> contributions = new ArrayList<>();
                while (result.next()) {
                    contributions.add(new ProviderContribution(
                            new ProviderId(result.getString("provider_id")),
                            result.getInt("precedence"),
                            result.getInt("required") != 0,
                            ProviderContributionStatus.valueOf(result.getString("status")),
                            result.getInt("item_count"),
                            Optional.ofNullable(result.getString("detail"))));
                }
                return List.copyOf(contributions);
            }
        }
    }

    private List<ProviderCompositionConflict> readConflicts(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT conflict_ordinal, entity_kind, logical_key, resolution,
                       winner_provider_id, winner_entity_id, winner_precedence, reason
                FROM snapshot_provider_conflict
                WHERE snapshot_id = ?
                ORDER BY conflict_ordinal
                """)) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<ProviderCompositionConflict> conflicts = new ArrayList<>();
                while (result.next()) {
                    int ordinal = result.getInt("conflict_ordinal");
                    String winnerProvider = result.getString("winner_provider_id");
                    Optional<ProviderConflictContender> winner = winnerProvider == null
                            ? Optional.empty()
                            : Optional.of(new ProviderConflictContender(
                                    new ProviderId(winnerProvider),
                                    result.getString("winner_entity_id"),
                                    result.getInt("winner_precedence")));
                    conflicts.add(new ProviderCompositionConflict(
                            ProviderEntityKind.valueOf(result.getString("entity_kind")),
                            result.getString("logical_key"),
                            ProviderConflictResolution.valueOf(result.getString("resolution")),
                            winner,
                            readContenders(snapshotId, ordinal),
                            result.getString("reason")));
                }
                return List.copyOf(conflicts);
            }
        }
    }

    private List<ProviderConflictContender> readContenders(KnowledgeSnapshotId snapshotId, int ordinal)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT provider_id, entity_id, precedence
                FROM snapshot_provider_conflict_contender
                WHERE snapshot_id = ? AND conflict_ordinal = ?
                ORDER BY precedence DESC, provider_id, entity_id
                """)) {
            statement.setString(1, snapshotId.toString());
            statement.setInt(2, ordinal);
            try (ResultSet result = statement.executeQuery()) {
                List<ProviderConflictContender> contenders = new ArrayList<>();
                while (result.next()) {
                    contenders.add(new ProviderConflictContender(
                            new ProviderId(result.getString("provider_id")),
                            result.getString("entity_id"),
                            result.getInt("precedence")));
                }
                return List.copyOf(contenders);
            }
        }
    }

    private boolean compositionExists(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM snapshot_provider_composition WHERE snapshot_id = ?")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean snapshotExists(KnowledgeSnapshotId snapshotId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM knowledge_snapshots WHERE id = ?")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot verify snapshot " + snapshotId, exception);
        }
    }

    private void configure(Connection opened) throws SQLException {
        try (Statement statement = opened.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new KnowledgeStoreException("SQLite provider composition report store is closed");
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original persistence error.
        }
    }

    private void restoreAutoCommit(boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot restore SQLite auto-commit mode", exception);
        }
    }

    private void closeQuietly(Connection opened) {
        if (opened == null) {
            return;
        }
        try {
            opened.close();
        } catch (SQLException ignored) {
            // Constructor failure is primary.
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
            throw new KnowledgeStoreException("Cannot close SQLite provider composition report store", exception);
        }
    }
}
