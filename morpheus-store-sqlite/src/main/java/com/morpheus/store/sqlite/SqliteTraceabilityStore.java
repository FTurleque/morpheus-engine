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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** SQLite adapter for snapshot-scoped traceability persistence introduced by M4-S2. */
public final class SqliteTraceabilityStore implements TraceabilityStore, AutoCloseable {
    private static final String INSERT_DEFINITION = """
            INSERT INTO traceability_links(
                link_id, source_kind, source_identity_id, relation_type,
                target_kind, target_identity_id, origin, resolution, confidence, observed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    /** Rows handed to the driver at a time, so that a large batch does not build an unbounded buffer. */
    private static final int BATCH_SLICE = 500;
    /** Identities looked up at a time; well under the bound SQLite puts on the parameters of one statement. */
    private static final int LOOKUP_SLICE = 200;

    private final Connection connection;
    private boolean closed;

    public SqliteTraceabilityStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        this.connection = SqliteStoreConnection.openAndMigrate(
                databasePath, "Cannot initialize SQLite traceability store", SqliteTraceabilityStore::configure);
    }

    /** Over a connection the caller opened on a migrated database, so that a test can count what the store prepares. */
    SqliteTraceabilityStore(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public synchronized void putLink(KnowledgeSnapshotId snapshotId, TraceabilityLink link) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(link, "link");
        try {
            requireSnapshot(snapshotId);
            SqliteTransactionRunner.runVoid(connection,
                    "Cannot store traceability link " + link.id(), ignored -> {
                putLinkInternal(snapshotId, link);
            });
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
            SqliteTransactionRunner.runVoid(connection, "Cannot store traceability link batch",
                    ignored -> putBatchInternal(snapshotId, batch));
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

    /**
     * Stores a batch with a number of prepared statements that does not depend on how many links it holds: two to
     * read what is already defined, three to write. Every statement is prepared once and run in slices, inside the
     * one transaction of the batch - a snapshot that held half of its links would be worse than a slow one.
     *
     * <p>The whole batch is compared with what exists before anything is written: a link already defined identically,
     * evidence included, is not rewritten; a different definition under the same identity refuses the batch and
     * names the identity.</p>
     */
    private void putBatchInternal(KnowledgeSnapshotId snapshotId, List<TraceabilityLink> batch) throws SQLException {
        Map<TraceabilityLinkId, TraceabilityLink> accepted = new LinkedHashMap<>();
        for (TraceabilityLink link : batch) {
            TraceabilityLink listedEarlier = accepted.putIfAbsent(link.id(), link);
            if (listedEarlier != null && !listedEarlier.equals(link)) {
                throw new KnowledgeStoreException("traceability link identity collision: " + link.id());
            }
        }
        Map<TraceabilityLinkId, TraceabilityLink> defined = findDefinitions(List.copyOf(accepted.keySet()));
        List<TraceabilityLink> undefined = new ArrayList<>();
        for (TraceabilityLink link : accepted.values()) {
            TraceabilityLink existing = defined.get(link.id());
            if (existing == null) {
                undefined.add(link);
            } else if (!existing.equals(link)) {
                throw new KnowledgeStoreException("traceability link identity collision: " + link.id());
            }
        }

        try (PreparedStatement definition = connection.prepareStatement(INSERT_DEFINITION)) {
            int pending = 0;
            for (TraceabilityLink link : undefined) {
                bindDefinition(definition, link);
                definition.addBatch();
                if (++pending == BATCH_SLICE) {
                    definition.executeBatch();
                    pending = 0;
                }
            }
            definition.executeBatch();
        }
        try (PreparedStatement evidence = connection.prepareStatement(
                "INSERT INTO traceability_link_evidence(link_id, evidence_id) VALUES (?, ?)")) {
            int pending = 0;
            for (TraceabilityLink link : undefined) {
                for (EvidenceId evidenceId : link.evidenceIds().stream().sorted().toList()) {
                    evidence.setString(1, link.id().toString());
                    evidence.setString(2, evidenceId.toString());
                    evidence.addBatch();
                    if (++pending == BATCH_SLICE) {
                        evidence.executeBatch();
                        pending = 0;
                    }
                }
            }
            evidence.executeBatch();
        }
        try (PreparedStatement membership = connection.prepareStatement(
                "INSERT OR IGNORE INTO snapshot_traceability_links(snapshot_id, link_id) VALUES (?, ?)")) {
            int pending = 0;
            for (TraceabilityLinkId linkId : accepted.keySet()) {
                membership.setString(1, snapshotId.toString());
                membership.setString(2, linkId.toString());
                membership.addBatch();
                if (++pending == BATCH_SLICE) {
                    membership.executeBatch();
                    pending = 0;
                }
            }
            membership.executeBatch();
        }
    }

    /**
     * The definitions that already exist among the given identities, evidence included. Two statements whatever the
     * number of identities: each is prepared once with a fixed number of placeholders and run per slice, the last
     * slice being padded with nulls, which match no row.
     */
    private Map<TraceabilityLinkId, TraceabilityLink> findDefinitions(List<TraceabilityLinkId> linkIds)
            throws SQLException {
        String placeholders = String.join(", ", Collections.nCopies(LOOKUP_SLICE, "?"));
        Map<TraceabilityLinkId, Set<EvidenceId>> evidenceByLink = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT link_id, evidence_id FROM traceability_link_evidence WHERE link_id IN (" + placeholders + ")")) {
            for (int from = 0; from < linkIds.size(); from += LOOKUP_SLICE) {
                bindSlice(statement, linkIds, from);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        evidenceByLink
                                .computeIfAbsent(TraceabilityLinkId.parse(result.getString(1)), absent -> new HashSet<>())
                                .add(EvidenceId.parse(result.getString(2)));
                    }
                }
            }
        }
        Map<TraceabilityLinkId, TraceabilityLink> definitions = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM traceability_links WHERE link_id IN (" + placeholders + ")")) {
            for (int from = 0; from < linkIds.size(); from += LOOKUP_SLICE) {
                bindSlice(statement, linkIds, from);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        TraceabilityLinkId linkId = TraceabilityLinkId.parse(result.getString("link_id"));
                        definitions.put(linkId, mapLink(
                                result, linkId, Set.copyOf(evidenceByLink.getOrDefault(linkId, Set.of()))));
                    }
                }
            }
        }
        return definitions;
    }

    private static void bindSlice(PreparedStatement statement, List<TraceabilityLinkId> linkIds, int from)
            throws SQLException {
        for (int offset = 0; offset < LOOKUP_SLICE; offset++) {
            int index = from + offset;
            if (index < linkIds.size()) {
                statement.setString(offset + 1, linkIds.get(index).toString());
            } else {
                statement.setNull(offset + 1, java.sql.Types.VARCHAR);
            }
        }
    }

    private void insertDefinition(TraceabilityLink link) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_DEFINITION)) {
            bindDefinition(statement, link);
            statement.executeUpdate();
        }
    }

    private static void bindDefinition(PreparedStatement statement, TraceabilityLink link) throws SQLException {
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
        return mapLink(result, linkId, evidence(linkId));
    }

    private static TraceabilityLink mapLink(ResultSet result, TraceabilityLinkId linkId, Set<EvidenceId> evidence)
            throws SQLException {
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
                evidence,
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

    private static void configure(Connection connection) throws SQLException {
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

}
