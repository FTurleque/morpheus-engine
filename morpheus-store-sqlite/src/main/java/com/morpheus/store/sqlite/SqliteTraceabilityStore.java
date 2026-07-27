package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.traceability.TraceabilityConfidence;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** SQLite adapter for snapshot-scoped traceability persistence introduced by M4-S2. */
public final class SqliteTraceabilityStore implements TraceabilityStore, AutoCloseable {
    private final Connection connection;
    private boolean closed;

    public SqliteTraceabilityStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Connection opened = null;
        try {
            opened = SqliteDatabaseSecurity.open(databasePath);
            configure(opened);
            new SqliteSchemaManager().migrate(opened);
            this.connection = opened;
        } catch (SQLException | RuntimeException exception) {
            closeQuietly(opened);
            if (exception instanceof KnowledgeStoreException knowledgeStoreException) {
                throw knowledgeStoreException;
            }
            throw new KnowledgeStoreException("Cannot initialize SQLite traceability store", exception);
        }
    }

    @Override
    public synchronized void putLink(KnowledgeSnapshotId snapshotId, TraceabilityLink link) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(link, "link");
        try {
            requireSnapshot(snapshotId);
            boolean previousAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                putLinkInternal(snapshotId, link);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollbackQuietly();
                throw exception;
            } finally {
                restoreAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot store traceability link " + link.id(), exception);
        }
    }

    @Override
    public synchronized void putLinks(KnowledgeSnapshotId snapshotId, List<TraceabilityLink> links) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        List<TraceabilityLink> batch = List.copyOf(Objects.requireNonNull(links, "links"));
        if (batch.isEmpty()) {
            return;
        }
        try {
            requireSnapshot(snapshotId);
            boolean previousAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                for (TraceabilityLink link : batch) {
                    putLinkInternal(snapshotId, link);
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                rollbackQuietly();
                throw failure;
            } finally {
                restoreAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot store traceability link batch", exception);
        }
    }

    @Override
    public synchronized Optional<TraceabilityLink> findLink(
            KnowledgeSnapshotId snapshotId,
            TraceabilityLinkId linkId) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(linkId, "linkId");
        try {
            requireSnapshot(snapshotId);
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT l.* FROM traceability_links l
                    JOIN snapshot_traceability_links m ON m.link_id = l.link_id
                    WHERE m.snapshot_id = ? AND l.link_id = ?
                    """)) {
                statement.setString(1, snapshotId.toString());
                statement.setString(2, linkId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(mapLink(result)) : Optional.empty();
                }
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read traceability link " + linkId, exception);
        }
    }

    @Override
    public synchronized List<TraceabilityLink> outgoing(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef source,
            Set<TraceabilityRelationType> relationTypes) {
        return endpointQuery(snapshotId, source, relationTypes, true);
    }

    @Override
    public synchronized List<TraceabilityLink> incoming(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef target,
            Set<TraceabilityRelationType> relationTypes) {
        return endpointQuery(snapshotId, target, relationTypes, false);
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
            throw new KnowledgeStoreException("Cannot close SQLite traceability store", exception);
        }
    }

    private List<TraceabilityLink> endpointQuery(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef endpoint,
            Set<TraceabilityRelationType> relationTypes,
            boolean outgoing) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(endpoint, "endpoint");
        Set<TraceabilityRelationType> filter = Set.copyOf(Objects.requireNonNull(relationTypes, "relationTypes"));
        String kindColumn = outgoing ? "source_kind" : "target_kind";
        String identityColumn = outgoing ? "source_identity_id" : "target_identity_id";
        try {
            requireSnapshot(snapshotId);
            String sql = "SELECT l.* FROM traceability_links l "
                    + "JOIN snapshot_traceability_links m ON m.link_id = l.link_id "
                    + "WHERE m.snapshot_id = ? AND l." + kindColumn + " = ? AND l." + identityColumn + " = ? "
                    + "ORDER BY l.link_id";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, snapshotId.toString());
                statement.setString(2, endpoint.kind().name());
                statement.setString(3, endpoint.identity().toString());
                try (ResultSet result = statement.executeQuery()) {
                    List<TraceabilityLink> links = new ArrayList<>();
                    while (result.next()) {
                        TraceabilityLink link = mapLink(result);
                        if (filter.isEmpty() || filter.contains(link.relationType())) {
                            links.add(link);
                        }
                    }
                    return List.copyOf(links);
                }
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot query snapshot traceability for " + endpoint, exception);
        }
    }

    private void insertDefinition(TraceabilityLink link) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO traceability_links(
                    link_id, source_kind, source_identity_id, relation_type,
                    target_kind, target_identity_id, origin, resolution, confidence, observed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, link.id().toString());
            statement.setString(2, link.source().kind().name());
            statement.setString(3, link.source().identity().toString());
            statement.setString(4, link.relationType().name());
            statement.setString(5, link.target().kind().name());
            statement.setString(6, link.target().identity().toString());
            statement.setString(7, link.origin().name());
            statement.setString(8, link.resolution().name());
            if (link.confidence().isPresent()) {
                statement.setDouble(9, link.confidence().orElseThrow().value());
            } else {
                statement.setNull(9, java.sql.Types.REAL);
            }
            statement.setString(10, link.observedAt().toString());
            statement.executeUpdate();
        }
    }

    private void insertEvidence(TraceabilityLink link) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO traceability_link_evidence(link_id, evidence_id) VALUES (?, ?)")) {
            for (EvidenceId evidenceId : link.evidenceIds().stream().sorted().toList()) {
                statement.setString(1, link.id().toString());
                statement.setString(2, evidenceId.toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void putLinkInternal(KnowledgeSnapshotId snapshotId, TraceabilityLink link) throws SQLException {
        Objects.requireNonNull(link, "link");
        Optional<TraceabilityLink> existing = findDefinitionInternal(link.id());
        if (existing.isPresent() && !existing.orElseThrow().equals(link)) {
            throw new KnowledgeStoreException("traceability link identity collision: " + link.id());
        }
        if (existing.isEmpty()) {
            insertDefinition(link);
            insertEvidence(link);
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO snapshot_traceability_links(snapshot_id, link_id) VALUES (?, ?)")) {
            statement.setString(1, snapshotId.toString());
            statement.setString(2, link.id().toString());
            statement.executeUpdate();
        }
    }

    private Optional<TraceabilityLink> findDefinitionInternal(TraceabilityLinkId linkId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM traceability_links WHERE link_id = ?")) {
            statement.setString(1, linkId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapLink(result)) : Optional.empty();
            }
        }
    }

    private TraceabilityLink mapLink(ResultSet result) throws SQLException {
        TraceabilityLinkId linkId = TraceabilityLinkId.parse(result.getString("link_id"));
        double rawConfidence = result.getDouble("confidence");
        Optional<TraceabilityConfidence> confidence = result.wasNull()
                ? Optional.empty()
                : Optional.of(TraceabilityConfidence.of(rawConfidence));
        return new TraceabilityLink(
                linkId,
                new TraceabilityEntityRef(
                        TraceabilityEntityKind.valueOf(result.getString("source_kind")),
                        DomainIdentity.parse(result.getString("source_identity_id"))),
                TraceabilityRelationType.valueOf(result.getString("relation_type")),
                new TraceabilityEntityRef(
                        TraceabilityEntityKind.valueOf(result.getString("target_kind")),
                        DomainIdentity.parse(result.getString("target_identity_id"))),
                TraceabilityLinkOrigin.valueOf(result.getString("origin")),
                TraceabilityResolutionState.valueOf(result.getString("resolution")),
                confidence,
                evidence(linkId),
                Instant.parse(result.getString("observed_at")));
    }

    private Set<EvidenceId> evidence(TraceabilityLinkId linkId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT evidence_id FROM traceability_link_evidence WHERE link_id = ? ORDER BY evidence_id")) {
            statement.setString(1, linkId.toString());
            try (ResultSet result = statement.executeQuery()) {
                Set<EvidenceId> evidence = new HashSet<>();
                while (result.next()) {
                    evidence.add(EvidenceId.parse(result.getString("evidence_id")));
                }
                return Set.copyOf(evidence);
            }
        }
    }

    private void requireSnapshot(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM knowledge_snapshots WHERE id = ?")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new KnowledgeStoreException("snapshot not found for traceability link: " + snapshotId);
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
            throw new KnowledgeStoreException("SQLite traceability store is closed");
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
