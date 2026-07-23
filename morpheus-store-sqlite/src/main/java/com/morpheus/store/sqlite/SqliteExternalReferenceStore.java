package com.morpheus.store.sqlite;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceResolutionEvent;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.reference.ResolvedExternalTarget;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.source.SourceLocator;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** SQLite adapter for immutable external-reference observations scoped to a knowledge snapshot. */
public final class SqliteExternalReferenceStore implements ExternalReferenceStore, AutoCloseable {
    private final Connection connection;
    private boolean closed;

    public SqliteExternalReferenceStore(Path databasePath) {
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
            throw new KnowledgeStoreException("Cannot initialize SQLite external reference store", exception);
        }
    }

    @Override
    public synchronized void putReference(KnowledgeSnapshotId snapshotId, ExternalReference reference) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(reference, "reference");
        try {
            requireSnapshot(snapshotId);
            Optional<ExternalReference> existing = findReferenceInternal(snapshotId, reference.id());
            if (existing.isPresent()) {
                if (!existing.orElseThrow().equals(reference)) {
                    throw new KnowledgeStoreException(
                            "external reference identity collision in snapshot: " + reference.id());
                }
                return;
            }

            boolean previousAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                insertReference(snapshotId, reference);
                insertResolvedAttributes(snapshotId, reference);
                insertHistory(snapshotId, reference);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollbackQuietly();
                throw exception;
            } finally {
                restoreAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot store external reference " + reference.id(), exception);
        }
    }

    @Override
    public synchronized Optional<ExternalReference> findReference(
            KnowledgeSnapshotId snapshotId,
            ExternalReferenceId referenceId) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(referenceId, "referenceId");
        try {
            requireSnapshot(snapshotId);
            return findReferenceInternal(snapshotId, referenceId);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read external reference " + referenceId, exception);
        }
    }

    @Override
    public synchronized List<ExternalReference> findByOwner(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity ownerId) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(ownerId, "ownerId");
        try {
            requireSnapshot(snapshotId);
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM snapshot_external_references
                    WHERE snapshot_id = ? AND owner_identity_id = ?
                    ORDER BY reference_id
                    """)) {
                statement.setString(1, snapshotId.toString());
                statement.setString(2, ownerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    List<ExternalReference> references = new ArrayList<>();
                    while (result.next()) {
                        references.add(mapReference(result));
                    }
                    return List.copyOf(references);
                }
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot query external references for owner " + ownerId, exception);
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
            throw new KnowledgeStoreException("Cannot close SQLite external reference store", exception);
        }
    }

    private Optional<ExternalReference> findReferenceInternal(
            KnowledgeSnapshotId snapshotId,
            ExternalReferenceId referenceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM snapshot_external_references
                WHERE snapshot_id = ? AND reference_id = ?
                """)) {
            statement.setString(1, snapshotId.toString());
            statement.setString(2, referenceId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapReference(result)) : Optional.empty();
            }
        }
    }

    private void insertReference(KnowledgeSnapshotId snapshotId, ExternalReference reference) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_external_references(
                    snapshot_id, reference_id, owner_identity_id,
                    system, project, resource_type, external_id, revision,
                    resolution_state, resolution_reason,
                    resolved_system, resolved_project, resolved_resource_type, resolved_external_id, resolved_revision,
                    provenance_provider_id, provenance_provider_version,
                    provenance_source_scheme, provenance_source_value,
                    provenance_external_id, provenance_source_revision, provenance_evidence_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ExternalReferenceTarget target = reference.target();
            statement.setString(1, snapshotId.toString());
            statement.setString(2, reference.id().toString());
            statement.setString(3, reference.ownerId().toString());
            statement.setString(4, target.system());
            statement.setString(5, target.project().orElse(null));
            statement.setString(6, target.resourceType());
            statement.setString(7, target.externalId());
            statement.setString(8, target.revision().orElse(null));
            statement.setString(9, reference.resolutionState().name());
            statement.setString(10, reference.resolutionReason().name());

            Optional<ResolvedExternalTarget> resolved = reference.resolvedTarget();
            if (resolved.isPresent()) {
                ExternalReferenceTarget resolvedTarget = resolved.orElseThrow().target();
                statement.setString(11, resolvedTarget.system());
                statement.setString(12, resolvedTarget.project().orElse(null));
                statement.setString(13, resolvedTarget.resourceType());
                statement.setString(14, resolvedTarget.externalId());
                statement.setString(15, resolvedTarget.revision().orElse(null));
            } else {
                for (int index = 11; index <= 15; index++) {
                    statement.setString(index, null);
                }
            }

            Optional<Provenance> provenance = reference.provenance();
            if (provenance.isPresent()) {
                Provenance value = provenance.orElseThrow();
                statement.setString(16, value.providerId().toString());
                statement.setString(17, value.providerVersion().orElse(null));
                statement.setString(18, value.source().scheme());
                statement.setString(19, value.source().value());
                statement.setString(20, value.externalId().orElse(null));
                statement.setString(21, value.sourceRevision().orElse(null));
                statement.setString(22, value.evidenceId().toString());
            } else {
                for (int index = 16; index <= 22; index++) {
                    statement.setString(index, null);
                }
            }
            statement.executeUpdate();
        }
    }

    private void insertResolvedAttributes(KnowledgeSnapshotId snapshotId, ExternalReference reference) throws SQLException {
        if (reference.resolvedTarget().isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_external_reference_attributes(
                    snapshot_id, reference_id, attribute_key, attribute_value
                ) VALUES (?, ?, ?, ?)
                """)) {
            for (Map.Entry<String, String> entry : reference.resolvedTarget().orElseThrow().attributes().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList()) {
                statement.setString(1, snapshotId.toString());
                statement.setString(2, reference.id().toString());
                statement.setString(3, entry.getKey());
                statement.setString(4, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertHistory(KnowledgeSnapshotId snapshotId, ExternalReference reference) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_external_reference_history(
                    snapshot_id, reference_id, event_index, previous_state, new_state, reason, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (int index = 0; index < reference.history().size(); index++) {
                ExternalReferenceResolutionEvent event = reference.history().get(index);
                statement.setString(1, snapshotId.toString());
                statement.setString(2, reference.id().toString());
                statement.setInt(3, index);
                statement.setString(4, event.previousState().name());
                statement.setString(5, event.newState().name());
                statement.setString(6, event.reason().name());
                statement.setString(7, event.occurredAt().toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private ExternalReference mapReference(ResultSet result) throws SQLException {
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.parse(result.getString("snapshot_id"));
        ExternalReferenceId referenceId = ExternalReferenceId.parse(result.getString("reference_id"));
        ExternalReferenceTarget target = new ExternalReferenceTarget(
                result.getString("system"),
                Optional.ofNullable(result.getString("project")),
                result.getString("resource_type"),
                result.getString("external_id"),
                Optional.ofNullable(result.getString("revision")));

        Optional<ResolvedExternalTarget> resolvedTarget = Optional.empty();
        String resolvedSystem = result.getString("resolved_system");
        if (resolvedSystem != null) {
            ExternalReferenceTarget resolvedCoordinates = new ExternalReferenceTarget(
                    resolvedSystem,
                    Optional.ofNullable(result.getString("resolved_project")),
                    result.getString("resolved_resource_type"),
                    result.getString("resolved_external_id"),
                    Optional.ofNullable(result.getString("resolved_revision")));
            resolvedTarget = Optional.of(new ResolvedExternalTarget(
                    resolvedCoordinates,
                    resolvedAttributes(snapshotId, referenceId)));
        }

        Optional<Provenance> provenance = Optional.empty();
        String providerId = result.getString("provenance_provider_id");
        if (providerId != null) {
            provenance = Optional.of(new Provenance(
                    new ProviderId(providerId),
                    Optional.ofNullable(result.getString("provenance_provider_version")),
                    new SourceLocator(
                            result.getString("provenance_source_scheme"),
                            result.getString("provenance_source_value")),
                    Optional.ofNullable(result.getString("provenance_external_id")),
                    Optional.ofNullable(result.getString("provenance_source_revision")),
                    EvidenceId.parse(result.getString("provenance_evidence_id"))));
        }

        return new ExternalReference(
                referenceId,
                DomainIdentity.parse(result.getString("owner_identity_id")),
                target,
                ExternalReferenceResolutionState.valueOf(result.getString("resolution_state")),
                ExternalReferenceResolutionReason.valueOf(result.getString("resolution_reason")),
                resolvedTarget,
                provenance,
                history(snapshotId, referenceId));
    }

    private Map<String, String> resolvedAttributes(
            KnowledgeSnapshotId snapshotId,
            ExternalReferenceId referenceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT attribute_key, attribute_value
                FROM snapshot_external_reference_attributes
                WHERE snapshot_id = ? AND reference_id = ?
                ORDER BY attribute_key
                """)) {
            statement.setString(1, snapshotId.toString());
            statement.setString(2, referenceId.toString());
            try (ResultSet result = statement.executeQuery()) {
                Map<String, String> attributes = new LinkedHashMap<>();
                while (result.next()) {
                    attributes.put(result.getString("attribute_key"), result.getString("attribute_value"));
                }
                return Map.copyOf(attributes);
            }
        }
    }

    private List<ExternalReferenceResolutionEvent> history(
            KnowledgeSnapshotId snapshotId,
            ExternalReferenceId referenceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT previous_state, new_state, reason, occurred_at
                FROM snapshot_external_reference_history
                WHERE snapshot_id = ? AND reference_id = ?
                ORDER BY event_index
                """)) {
            statement.setString(1, snapshotId.toString());
            statement.setString(2, referenceId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<ExternalReferenceResolutionEvent> events = new ArrayList<>();
                while (result.next()) {
                    events.add(new ExternalReferenceResolutionEvent(
                            ExternalReferenceResolutionState.valueOf(result.getString("previous_state")),
                            ExternalReferenceResolutionState.valueOf(result.getString("new_state")),
                            ExternalReferenceResolutionReason.valueOf(result.getString("reason")),
                            Instant.parse(result.getString("occurred_at"))));
                }
                return List.copyOf(events);
            }
        }
    }

    private void requireSnapshot(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM knowledge_snapshots WHERE id = ?")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new KnowledgeStoreException("snapshot not found for external reference: " + snapshotId);
                }
            }
        }
    }

    private void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new KnowledgeStoreException("SQLite external reference store is closed");
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

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Initialization is already failing.
        }
    }
}
