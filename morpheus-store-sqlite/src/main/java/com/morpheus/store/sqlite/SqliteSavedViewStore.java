package com.morpheus.store.sqlite;

import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryDefinitionCodec;
import com.morpheus.application.query.dsl.QueryScope;
import com.morpheus.application.query.saved.SavedViewConflictException;
import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.application.query.saved.SavedViewVersion;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SavedViewStore;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** SQLite V014 adapter for M24 saved query definitions and immutable revision history. */
public final class SqliteSavedViewStore implements SavedViewStore, AutoCloseable {
    private final Connection connection;
    private final QueryDefinitionCodec codec = new QueryDefinitionCodec();
    private boolean closed;

    public SqliteSavedViewStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Connection opened = null;
        try {
            opened = SqliteDatabaseSecurity.open(databasePath);
            new SqliteSchemaManager().migrate(opened);
            this.connection = opened;
        } catch (SQLException | RuntimeException failure) {
            closeQuietly(opened);
            if (failure instanceof KnowledgeStoreException knowledgeStoreException) {
                throw knowledgeStoreException;
            }
            throw new KnowledgeStoreException("Cannot initialize SQLite saved-view store", failure);
        }
    }

    @Override
    public synchronized void create(SavedViewDefinition definition, SavedViewVersion version) {
        ensureOpen();
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(version, "version");
        if (!definition.id().equals(version.id()) || definition.revision() != version.revision()) {
            throw new IllegalArgumentException("saved view definition/version identity or revision mismatch");
        }
        if (find(definition.id()).isPresent()) {
            throw new SavedViewConflictException("saved view already exists: " + definition.id());
        }
        transaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO saved_views(
                        id, scope_kind, scope_id, name, query_definition,
                        revision, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                bindDefinition(statement, definition);
                statement.executeUpdate();
            }
            insertVersion(version);
            return null;
        }, "Cannot persist saved view " + definition.id());
    }

    @Override
    public synchronized Optional<SavedViewDefinition> find(SavedViewId id) {
        ensureOpen();
        Objects.requireNonNull(id, "id");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, scope_kind, scope_id, name, query_definition,
                       revision, status, created_at, updated_at
                FROM saved_views
                WHERE id = ?
                """)) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readDefinition(result)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot read saved view " + id, failure);
        }
    }

    @Override
    public synchronized List<SavedViewDefinition> list(QueryScope scope) {
        ensureOpen();
        Objects.requireNonNull(scope, "scope");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, scope_kind, scope_id, name, query_definition,
                       revision, status, created_at, updated_at
                FROM saved_views
                WHERE scope_kind = ? AND scope_id = ?
                ORDER BY name COLLATE NOCASE, id
                """)) {
            statement.setString(1, scopeKind(scope));
            statement.setString(2, scopeId(scope));
            try (ResultSet result = statement.executeQuery()) {
                List<SavedViewDefinition> values = new ArrayList<>();
                while (result.next()) {
                    values.add(readDefinition(result));
                }
                return List.copyOf(values);
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot list saved views", failure);
        }
    }

    @Override
    public synchronized List<SavedViewVersion> listVersions(SavedViewId id) {
        ensureOpen();
        Objects.requireNonNull(id, "id");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT saved_view_id, revision, name, query_definition, status, recorded_at
                FROM saved_view_versions
                WHERE saved_view_id = ?
                ORDER BY revision
                """)) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<SavedViewVersion> values = new ArrayList<>();
                while (result.next()) {
                    values.add(readVersion(result));
                }
                return List.copyOf(values);
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot list saved-view versions " + id, failure);
        }
    }

    @Override
    public synchronized long count(QueryScope scope) {
        ensureOpen();
        Objects.requireNonNull(scope, "scope");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM saved_views WHERE scope_kind = ? AND scope_id = ?")) {
            statement.setString(1, scopeKind(scope));
            statement.setString(2, scopeId(scope));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot count saved views", failure);
        }
    }

    @Override
    public synchronized SavedViewDefinition compareAndSet(
            SavedViewId id,
            long expectedRevision,
            SavedViewDefinition replacement,
            SavedViewVersion version) {
        ensureOpen();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(version, "version");
        if (!replacement.id().equals(id) || !version.id().equals(id)) {
            throw new IllegalArgumentException("saved view replacement identity mismatch");
        }
        long nextRevision = expectedRevision + 1;
        if (replacement.revision() != nextRevision || version.revision() != nextRevision) {
            throw new IllegalArgumentException("saved view replacement must advance revision by exactly one");
        }
        SavedViewDefinition before = find(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown saved view: " + id));
        if (!before.query().scope().equals(replacement.query().scope())) {
            throw new IllegalArgumentException("saved view scope is immutable");
        }

        Integer changed = transaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE saved_views
                    SET name = ?, query_definition = ?, revision = ?, status = ?, updated_at = ?
                    WHERE id = ? AND revision = ?
                    """)) {
                statement.setString(1, replacement.name());
                statement.setString(2, codec.encode(replacement.query()));
                statement.setLong(3, replacement.revision());
                statement.setString(4, replacement.status().name());
                statement.setString(5, replacement.updatedAt().toString());
                statement.setString(6, id.toString());
                statement.setLong(7, expectedRevision);
                int updated = statement.executeUpdate();
                if (updated == 1) {
                    insertVersion(version);
                }
                return updated;
            }
        }, "Cannot update saved view " + id);

        if (changed != 1) {
            SavedViewDefinition current = find(id)
                    .orElseThrow(() -> new IllegalArgumentException("unknown saved view: " + id));
            throw new SavedViewConflictException(
                    "stale saved view revision: expected " + expectedRevision + " but current is " + current.revision());
        }
        return replacement;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            connection.close();
            closed = true;
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot close SQLite saved-view store", failure);
        }
    }

    private void bindDefinition(PreparedStatement statement, SavedViewDefinition definition) throws SQLException {
        statement.setString(1, definition.id().toString());
        statement.setString(2, scopeKind(definition.query().scope()));
        statement.setString(3, scopeId(definition.query().scope()));
        statement.setString(4, definition.name());
        statement.setString(5, codec.encode(definition.query()));
        statement.setLong(6, definition.revision());
        statement.setString(7, definition.status().name());
        statement.setString(8, definition.createdAt().toString());
        statement.setString(9, definition.updatedAt().toString());
    }

    private void insertVersion(SavedViewVersion version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO saved_view_versions(
                    saved_view_id, revision, name, query_definition, status, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, version.id().toString());
            statement.setLong(2, version.revision());
            statement.setString(3, version.name());
            statement.setString(4, codec.encode(version.query()));
            statement.setString(5, version.status().name());
            statement.setString(6, version.recordedAt().toString());
            statement.executeUpdate();
        }
    }

    private SavedViewDefinition readDefinition(ResultSet result) throws SQLException {
        QueryDefinition query = codec.decode(result.getString("query_definition"));
        requireStoredScope(result.getString("scope_kind"), result.getString("scope_id"), query.scope());
        return new SavedViewDefinition(
                SavedViewId.parse(result.getString("id")),
                result.getString("name"),
                query,
                result.getLong("revision"),
                SavedViewStatus.valueOf(result.getString("status")),
                Instant.parse(result.getString("created_at")),
                Instant.parse(result.getString("updated_at")));
    }

    private SavedViewVersion readVersion(ResultSet result) throws SQLException {
        return new SavedViewVersion(
                SavedViewId.parse(result.getString("saved_view_id")),
                result.getLong("revision"),
                result.getString("name"),
                codec.decode(result.getString("query_definition")),
                SavedViewStatus.valueOf(result.getString("status")),
                Instant.parse(result.getString("recorded_at")));
    }

    private void requireStoredScope(String kind, String id, QueryScope scope) {
        if (!scopeKind(scope).equals(kind) || !scopeId(scope).equals(id)) {
            throw new KnowledgeStoreException("saved view scope columns disagree with encoded query definition");
        }
    }

    private String scopeKind(QueryScope scope) {
        if (scope instanceof ProjectQueryScope) {
            return "PROJECT";
        }
        if (scope instanceof PortfolioQueryScope) {
            return "PORTFOLIO";
        }
        throw new IllegalArgumentException("unsupported query scope: " + scope.getClass().getName());
    }

    private String scopeId(QueryScope scope) {
        if (scope instanceof ProjectQueryScope project) {
            return project.projectId().toString();
        }
        if (scope instanceof PortfolioQueryScope portfolio) {
            return portfolio.portfolioId().toString();
        }
        throw new IllegalArgumentException("unsupported query scope: " + scope.getClass().getName());
    }

    private <T> T transaction(SqlWork<T> work, String message) {
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            T value = work.run();
            connection.commit();
            return value;
        } catch (SQLException | RuntimeException failure) {
            rollbackQuietly();
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new KnowledgeStoreException(message, failure);
        } finally {
            try {
                if (!connection.isClosed()) {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException failure) {
                throw new KnowledgeStoreException("Cannot restore SQLite auto-commit mode", failure);
            }
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original failure.
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SQLite saved-view store is closed");
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Preserve initialization failure.
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run() throws SQLException;
    }
}
