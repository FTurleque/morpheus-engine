package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotConflictException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** SQLite adapter for the MORPHEUS knowledge-store foundation. */
public final class SqliteSpecificationKnowledgeStore implements SpecificationKnowledgeStore, AutoCloseable {
    private final Connection connection;
    private boolean closed;

    public SqliteSpecificationKnowledgeStore(Path databasePath) {
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
            throw new KnowledgeStoreException("Cannot initialize SQLite knowledge store", exception);
        }
    }

    @Override
    public synchronized void putProject(ProjectStoreEntry project) {
        ensureOpen();
        try {
            Optional<ProjectStoreEntry> existing = findProjectInternal(project.id());
            if (existing.isPresent()) {
                if (!existing.orElseThrow().equals(project)) {
                    throw new KnowledgeStoreException("project identity collision: " + project.id());
                }
                return;
            }

            Optional<ProjectStoreEntry> rootOwner = findProjectByRootInternal(project.rootLocator());
            if (rootOwner.isPresent()) {
                throw new KnowledgeStoreException(
                        "project root already registered by another identity: " + rootOwner.orElseThrow().id());
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO projects(id, root_scheme, root_value) VALUES (?, ?, ?)")) {
                statement.setString(1, project.id().toString());
                statement.setString(2, project.rootLocator().scheme());
                statement.setString(3, project.rootLocator().value());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot store project " + project.id(), exception);
        }
    }

    @Override
    public synchronized Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) {
        ensureOpen();
        try {
            return findProjectInternal(projectId);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read project " + projectId, exception);
        }
    }

    @Override
    public synchronized Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) {
        ensureOpen();
        Objects.requireNonNull(rootLocator, "rootLocator");
        try {
            return findProjectByRootInternal(rootLocator);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read project by root " + rootLocator, exception);
        }
    }

    @Override
    public synchronized List<ProjectStoreEntry> listProjects() {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, root_scheme, root_value FROM projects ORDER BY id");
             ResultSet result = statement.executeQuery()) {
            List<ProjectStoreEntry> projects = new ArrayList<>();
            while (result.next()) {
                projects.add(mapProject(result));
            }
            return List.copyOf(projects);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot list registered projects", exception);
        }
    }

    @Override
    public synchronized void putSnapshot(KnowledgeSnapshotMetadata snapshot) {
        ensureOpen();
        if (snapshot.state() == KnowledgeSnapshotState.ACTIVE
                || snapshot.state() == KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException("ACTIVE/RETIRED snapshots must be produced by activation lifecycle");
        }

        try {
            Optional<KnowledgeSnapshotMetadata> existing = findSnapshotInternal(snapshot.id());
            if (existing.isPresent()) {
                if (!existing.orElseThrow().sameDefinitionAs(snapshot)) {
                    throw new KnowledgeStoreException("snapshot identity collision: " + snapshot.id());
                }
                return;
            }

            validateSnapshotReferences(snapshot);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO knowledge_snapshots(
                        id, project_id, predecessor_id, state, source_revision, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, snapshot.id().toString());
                statement.setString(2, snapshot.projectId().toString());
                statement.setString(3, snapshot.predecessorId().map(KnowledgeSnapshotId::toString).orElse(null));
                statement.setString(4, snapshot.state().name());
                statement.setString(5, snapshot.sourceRevision().orElse(null));
                statement.setString(6, snapshot.createdAt().toString());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot store snapshot " + snapshot.id(), exception);
        }
    }

    @Override
    public synchronized Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) {
        ensureOpen();
        try {
            return findSnapshotInternal(snapshotId);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read snapshot " + snapshotId, exception);
        }
    }

    @Override
    public synchronized Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
        ensureOpen();
        try {
            return activeSnapshotInternal(projectId);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read active snapshot for project " + projectId, exception);
        }
    }

    @Override
    public synchronized KnowledgeSnapshotMetadata transitionSnapshotState(
            KnowledgeSnapshotId snapshotId,
            KnowledgeSnapshotState expectedState,
            KnowledgeSnapshotState targetState) {
        ensureOpen();
        rejectPublishedTargetState(targetState);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE knowledge_snapshots
                SET state = ?
                WHERE id = ? AND state = ?
                """)) {
            statement.setString(1, targetState.name());
            statement.setString(2, snapshotId.toString());
            statement.setString(3, expectedState.name());
            if (statement.executeUpdate() == 1) {
                return findSnapshotInternal(snapshotId).orElseThrow();
            }

            KnowledgeSnapshotMetadata current = findSnapshotInternal(snapshotId)
                    .orElseThrow(() -> new KnowledgeStoreException("snapshot not found: " + snapshotId));
            throw new SnapshotConflictException(
                    "snapshot state changed: expected " + expectedState + " but was " + current.state());
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot transition snapshot " + snapshotId, exception);
        }
    }

    @Override
    public synchronized KnowledgeSnapshotMetadata activateSnapshot(
            KnowledgeSnapshotId snapshotId,
            Optional<KnowledgeSnapshotId> expectedActiveSnapshotId) {
        ensureOpen();
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot begin SQLite snapshot activation", exception);
        }

        try {
            KnowledgeSnapshotMetadata target = findSnapshotInternal(snapshotId)
                    .orElseThrow(() -> new KnowledgeStoreException("snapshot not found: " + snapshotId));
            Optional<KnowledgeSnapshotMetadata> active = activeSnapshotInternal(target.projectId());

            if (target.state() == KnowledgeSnapshotState.ACTIVE) {
                if (active.map(KnowledgeSnapshotMetadata::id).equals(Optional.of(snapshotId))) {
                    connection.commit();
                    return target;
                }
                throw new SnapshotConflictException(
                        "active snapshot state is inconsistent for project " + target.projectId());
            }

            if (target.state() != KnowledgeSnapshotState.READY) {
                throw new SnapshotConflictException("only READY snapshots can be activated: " + snapshotId);
            }
            if (!target.predecessorId().equals(expectedActiveSnapshotId)) {
                throw new SnapshotConflictException("snapshot predecessor does not match expected active snapshot");
            }

            Optional<KnowledgeSnapshotId> currentActiveId = active.map(KnowledgeSnapshotMetadata::id);
            if (!currentActiveId.equals(expectedActiveSnapshotId)) {
                throw new SnapshotConflictException("active snapshot changed before activation");
            }

            if (active.isPresent()) {
                updateSnapshotState(active.orElseThrow().id(), KnowledgeSnapshotState.RETIRED);
            }
            updateSnapshotState(snapshotId, KnowledgeSnapshotState.ACTIVE);

            connection.commit();
            return target.withState(KnowledgeSnapshotState.ACTIVE);
        } catch (SQLException exception) {
            rollbackQuietly();
            throw new KnowledgeStoreException("SQLite snapshot activation failed", exception);
        } catch (RuntimeException exception) {
            rollbackQuietly();
            throw exception;
        } finally {
            restoreAutoCommit(previousAutoCommit);
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
            throw new KnowledgeStoreException("Cannot close SQLite knowledge store", exception);
        }
    }

    private void validateSnapshotReferences(KnowledgeSnapshotMetadata snapshot) throws SQLException {
        if (findProjectInternal(snapshot.projectId()).isEmpty()) {
            throw new KnowledgeStoreException("project not found: " + snapshot.projectId());
        }

        if (snapshot.predecessorId().isPresent()) {
            KnowledgeSnapshotMetadata predecessor = findSnapshotInternal(snapshot.predecessorId().orElseThrow())
                    .orElseThrow(() -> new KnowledgeStoreException(
                            "snapshot predecessor not found: " + snapshot.predecessorId().orElseThrow()));
            if (!predecessor.projectId().equals(snapshot.projectId())) {
                throw new KnowledgeStoreException("snapshot predecessor belongs to another project");
            }
        }
    }

    private Optional<ProjectStoreEntry> findProjectInternal(ProjectSpecificationId projectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, root_scheme, root_value FROM projects WHERE id = ?")) {
            statement.setString(1, projectId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapProject(result)) : Optional.empty();
            }
        }
    }

    private Optional<ProjectStoreEntry> findProjectByRootInternal(SourceLocator rootLocator) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, root_scheme, root_value FROM projects WHERE root_scheme = ? AND root_value = ?")) {
            statement.setString(1, rootLocator.scheme());
            statement.setString(2, rootLocator.value());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapProject(result)) : Optional.empty();
            }
        }
    }

    private ProjectStoreEntry mapProject(ResultSet result) throws SQLException {
        return new ProjectStoreEntry(
                ProjectSpecificationId.parse(result.getString("id")),
                new SourceLocator(result.getString("root_scheme"), result.getString("root_value")));
    }

    private Optional<KnowledgeSnapshotMetadata> findSnapshotInternal(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT project_id, predecessor_id, state, source_revision, created_at
                FROM knowledge_snapshots
                WHERE id = ?
                """)) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapSnapshot(snapshotId, result)) : Optional.empty();
            }
        }
    }

    private Optional<KnowledgeSnapshotMetadata> activeSnapshotInternal(ProjectSpecificationId projectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, predecessor_id, state, source_revision, created_at
                FROM knowledge_snapshots
                WHERE project_id = ? AND state = 'ACTIVE'
                """)) {
            statement.setString(1, projectId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.parse(result.getString("id"));
                return Optional.of(mapSnapshot(snapshotId, projectId, result));
            }
        }
    }

    private KnowledgeSnapshotMetadata mapSnapshot(KnowledgeSnapshotId snapshotId, ResultSet result) throws SQLException {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(result.getString("project_id"));
        return mapSnapshot(snapshotId, projectId, result);
    }

    private KnowledgeSnapshotMetadata mapSnapshot(
            KnowledgeSnapshotId snapshotId,
            ProjectSpecificationId projectId,
            ResultSet result) throws SQLException {
        String predecessor = result.getString("predecessor_id");
        String sourceRevision = result.getString("source_revision");
        return new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                predecessor == null ? Optional.empty() : Optional.of(KnowledgeSnapshotId.parse(predecessor)),
                KnowledgeSnapshotState.valueOf(result.getString("state")),
                Optional.ofNullable(sourceRevision),
                Instant.parse(result.getString("created_at")));
    }

    private void updateSnapshotState(KnowledgeSnapshotId snapshotId, KnowledgeSnapshotState state) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE knowledge_snapshots SET state = ? WHERE id = ?")) {
            statement.setString(1, state.name());
            statement.setString(2, snapshotId.toString());
            if (statement.executeUpdate() != 1) {
                throw new KnowledgeStoreException("snapshot not found during state update: " + snapshotId);
            }
        }
    }

    private void rejectPublishedTargetState(KnowledgeSnapshotState targetState) {
        if (targetState == KnowledgeSnapshotState.ACTIVE || targetState == KnowledgeSnapshotState.RETIRED) {
            throw new SnapshotConflictException("ACTIVE/RETIRED states are owned by snapshot activation");
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
            throw new KnowledgeStoreException("SQLite knowledge store is closed");
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original activation error.
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
