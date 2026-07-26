package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;

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

/** SQLite adapter for versioned Requirement persistence introduced by M3-S4. */
public final class SqliteVersionedRequirementStore implements VersionedRequirementStore, AutoCloseable {
    private final Connection connection;
    private boolean closed;

    public SqliteVersionedRequirementStore(Path databasePath) {
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
            throw new KnowledgeStoreException("Cannot initialize SQLite versioned requirement store", exception);
        }
    }

    @Override
    public synchronized void putSpecificationVersion(SpecificationVersion version) {
        ensureOpen();
        Objects.requireNonNull(version, "version");
        try {
            if (!projectExists(version.projectId())) {
                throw new KnowledgeStoreException("project not found for specification version: " + version.projectId());
            }
            if (version.predecessor().isPresent()) {
                SpecificationVersion predecessor = findSpecificationVersionInternal(version.predecessor().orElseThrow())
                        .orElseThrow(() -> new KnowledgeStoreException(
                                "specification version predecessor not found: " + version.predecessor().orElseThrow()));
                if (!predecessor.projectId().equals(version.projectId())) {
                    throw new KnowledgeStoreException("specification version predecessor belongs to another project");
                }
            }

            Optional<SpecificationVersion> existing = findSpecificationVersionInternal(version.id());
            if (existing.isPresent()) {
                if (!existing.orElseThrow().equals(version)) {
                    throw new KnowledgeStoreException("specification version identity collision: " + version.id());
                }
                return;
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO specification_versions(
                        id, project_id, sequence, provider_version, source_revision, created_at, predecessor_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, version.id().toString());
                statement.setString(2, version.projectId().toString());
                if (version.sequence().isPresent()) {
                    statement.setLong(3, version.sequence().orElseThrow());
                } else {
                    statement.setNull(3, java.sql.Types.BIGINT);
                }
                statement.setString(4, version.providerVersion().orElse(null));
                statement.setString(5, version.sourceRevision().orElse(null));
                statement.setString(6, version.createdAt().toString());
                statement.setString(7, version.predecessor().map(SpecificationVersionId::toString).orElse(null));
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot store specification version " + version.id(), exception);
        }
    }

    @Override
    public synchronized Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) {
        ensureOpen();
        try {
            return findSpecificationVersionInternal(versionId);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read specification version " + versionId, exception);
        }
    }

    @Override
    public synchronized void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) {
        ensureOpen();
        Objects.requireNonNull(binding, "binding");
        try {
            ProjectSpecificationId snapshotProject = snapshotProject(binding.snapshotId())
                    .orElseThrow(() -> new KnowledgeStoreException("snapshot not found: " + binding.snapshotId()));
            SpecificationVersion version = findSpecificationVersionInternal(binding.specificationVersionId())
                    .orElseThrow(() -> new KnowledgeStoreException(
                            "specification version not found: " + binding.specificationVersionId()));
            if (!snapshotProject.equals(version.projectId())) {
                throw new KnowledgeStoreException("snapshot and specification version belong to different projects");
            }

            Optional<SnapshotSpecificationVersionBinding> existing = findSnapshotVersionInternal(binding.snapshotId());
            if (existing.isPresent()) {
                if (!existing.orElseThrow().equals(binding)) {
                    throw new KnowledgeStoreException("snapshot already bound to another specification version");
                }
                return;
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO snapshot_specification_versions(snapshot_id, specification_version_id) VALUES (?, ?)")) {
                statement.setString(1, binding.snapshotId().toString());
                statement.setString(2, binding.specificationVersionId().toString());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot bind snapshot to specification version " + binding.snapshotId(), exception);
        }
    }

    @Override
    public synchronized Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) {
        ensureOpen();
        try {
            return findSnapshotVersionInternal(snapshotId);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read snapshot specification version binding " + snapshotId, exception);
        }
    }

    @Override
    public synchronized void putRequirementVersion(RequirementVersionRecord record) {
        ensureOpen();
        Objects.requireNonNull(record, "record");
        try {
            SnapshotSpecificationVersionBinding binding = findSnapshotVersionInternal(record.snapshotId())
                    .orElseThrow(() -> new KnowledgeStoreException(
                            "snapshot has no specification version binding: " + record.snapshotId()));
            if (!binding.specificationVersionId().equals(record.entityVersion().specificationVersionId())) {
                throw new KnowledgeStoreException("requirement version does not match snapshot specification version");
            }

            Optional<RequirementVersionRecord> existing = findRequirementVersionInternal(record.entityVersion().id());
            if (existing.isPresent()) {
                if (!existing.orElseThrow().equals(record)) {
                    throw new KnowledgeStoreException("entity version identity collision: " + record.entityVersion().id());
                }
                return;
            }

            if (record.entityVersion().temporalState() == TemporalState.CURRENT
                    && currentRequirementInternal(record.snapshotId(), record.entityVersion().entityIdentity()).isPresent()) {
                throw new KnowledgeStoreException(
                        "multiple CURRENT requirement versions for the same identity in one snapshot");
            }

            Requirement requirement = record.entityVersion().content();
            Provenance provenance = requirement.provenance();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO requirement_versions(
                        entity_version_id, entity_identity_id, requirement_id, specification_id,
                        specification_version_id, snapshot_id, temporal_state, requirement_key,
                        title, statement, provider_id, provider_version, source_scheme, source_value,
                        external_id, source_revision, evidence_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, record.entityVersion().id().toString());
                statement.setString(2, record.entityVersion().entityIdentity().toString());
                statement.setString(3, requirement.id().toString());
                statement.setString(4, requirement.specificationId().toString());
                statement.setString(5, record.entityVersion().specificationVersionId().toString());
                statement.setString(6, record.snapshotId().toString());
                statement.setString(7, record.entityVersion().temporalState().name());
                statement.setString(8, requirement.key().orElse(null));
                statement.setString(9, requirement.title());
                statement.setString(10, requirement.statement());
                statement.setString(11, provenance.providerId().value());
                statement.setString(12, provenance.providerVersion().orElse(null));
                statement.setString(13, provenance.source().scheme());
                statement.setString(14, provenance.source().value());
                statement.setString(15, provenance.externalId().orElse(null));
                statement.setString(16, provenance.sourceRevision().orElse(null));
                statement.setString(17, provenance.evidenceId().toString());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot store requirement version " + record.entityVersion().id(), exception);
        }
    }

    @Override
    public synchronized void putRequirementVersions(List<RequirementVersionRecord> records) {
        ensureOpen();
        List<RequirementVersionRecord> batch = List.copyOf(Objects.requireNonNull(records, "records"));
        if (batch.isEmpty()) {
            return;
        }
        try {
            boolean previousAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                batch.forEach(this::putRequirementVersion);
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                rollbackQuietly();
                throw failure;
            } finally {
                restoreAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot store requirement version batch", exception);
        }
    }

    @Override
    public synchronized Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) {
        ensureOpen();
        try {
            return findRequirementVersionInternal(entityVersionId);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read requirement version " + entityVersionId, exception);
        }
    }

    @Override
    public synchronized List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM requirement_versions
                WHERE snapshot_id = ?
                ORDER BY entity_version_id
                """)) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<RequirementVersionRecord> records = new ArrayList<>();
                while (result.next()) {
                    records.add(mapRequirementVersion(result));
                }
                return List.copyOf(records);
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot list requirement versions for snapshot " + snapshotId, exception);
        }
    }

    @Override
    public synchronized Optional<RequirementVersionRecord> currentRequirement(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity entityIdentity) {
        ensureOpen();
        try {
            return currentRequirementInternal(snapshotId, entityIdentity);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read CURRENT requirement " + entityIdentity, exception);
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
            throw new KnowledgeStoreException("Cannot close SQLite versioned requirement store", exception);
        }
    }

    private Optional<SpecificationVersion> findSpecificationVersionInternal(SpecificationVersionId versionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT project_id, sequence, provider_version, source_revision, created_at, predecessor_id
                FROM specification_versions
                WHERE id = ?
                """)) {
            statement.setString(1, versionId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                long sequence = result.getLong("sequence");
                boolean sequenceWasNull = result.wasNull();
                String predecessor = result.getString("predecessor_id");
                return Optional.of(new SpecificationVersion(
                        versionId,
                        ProjectSpecificationId.parse(result.getString("project_id")),
                        sequenceWasNull ? Optional.empty() : Optional.of(sequence),
                        Optional.ofNullable(result.getString("provider_version")),
                        Optional.ofNullable(result.getString("source_revision")),
                        Instant.parse(result.getString("created_at")),
                        predecessor == null ? Optional.empty() : Optional.of(SpecificationVersionId.parse(predecessor))));
            }
        }
    }

    private Optional<SnapshotSpecificationVersionBinding> findSnapshotVersionInternal(KnowledgeSnapshotId snapshotId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT specification_version_id FROM snapshot_specification_versions WHERE snapshot_id = ?")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SnapshotSpecificationVersionBinding(
                        snapshotId,
                        SpecificationVersionId.parse(result.getString("specification_version_id"))));
            }
        }
    }

    private Optional<RequirementVersionRecord> findRequirementVersionInternal(EntityVersionId entityVersionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM requirement_versions WHERE entity_version_id = ?")) {
            statement.setString(1, entityVersionId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapRequirementVersion(result)) : Optional.empty();
            }
        }
    }

    private Optional<RequirementVersionRecord> currentRequirementInternal(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity entityIdentity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM requirement_versions
                WHERE snapshot_id = ? AND entity_identity_id = ? AND temporal_state = 'CURRENT'
                """)) {
            statement.setString(1, snapshotId.toString());
            statement.setString(2, entityIdentity.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapRequirementVersion(result)) : Optional.empty();
            }
        }
    }

    private RequirementVersionRecord mapRequirementVersion(ResultSet result) throws SQLException {
        Requirement requirement = new Requirement(
                RequirementId.parse(result.getString("requirement_id")),
                SpecificationId.parse(result.getString("specification_id")),
                Optional.ofNullable(result.getString("requirement_key")),
                result.getString("title"),
                result.getString("statement"),
                new Provenance(
                        new ProviderId(result.getString("provider_id")),
                        Optional.ofNullable(result.getString("provider_version")),
                        new SourceLocator(result.getString("source_scheme"), result.getString("source_value")),
                        Optional.ofNullable(result.getString("external_id")),
                        Optional.ofNullable(result.getString("source_revision")),
                        EvidenceId.parse(result.getString("evidence_id"))));

        EntityVersion<Requirement> entityVersion = new EntityVersion<>(
                EntityVersionId.parse(result.getString("entity_version_id")),
                DomainIdentity.parse(result.getString("entity_identity_id")),
                SpecificationVersionId.parse(result.getString("specification_version_id")),
                TemporalState.valueOf(result.getString("temporal_state")),
                requirement);

        return new RequirementVersionRecord(
                KnowledgeSnapshotId.parse(result.getString("snapshot_id")),
                entityVersion);
    }

    private boolean projectExists(ProjectSpecificationId projectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM projects WHERE id = ?")) {
            statement.setString(1, projectId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private Optional<ProjectSpecificationId> snapshotProject(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT project_id FROM knowledge_snapshots WHERE id = ?")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(ProjectSpecificationId.parse(result.getString("project_id")))
                        : Optional.empty();
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
            throw new KnowledgeStoreException("SQLite versioned requirement store is closed");
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

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original batch failure.
        }
    }

    private void restoreAutoCommit(boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot restore SQLite auto-commit mode", exception);
        }
    }
}
