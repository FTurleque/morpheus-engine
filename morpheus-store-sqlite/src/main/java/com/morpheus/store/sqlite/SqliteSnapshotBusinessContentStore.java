package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.version.SpecificationVersionId;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** SQLite adapter for snapshot-owned non-Requirement business-content projection. */
public final class SqliteSnapshotBusinessContentStore implements SnapshotBusinessContentStore, AutoCloseable {
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    private final Connection connection;
    private final SqliteSnapshotBusinessContentReader reader;
    private final SqliteSnapshotBusinessContentWriter writer;
    private boolean closed;

    public SqliteSnapshotBusinessContentStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        this.connection = SqliteStoreConnection.openAndMigrate(
                databasePath, "Cannot initialize SQLite snapshot business content store", SqliteSnapshotBusinessContentStore::configure);
        this.reader = new SqliteSnapshotBusinessContentReader(connection);
        this.writer = new SqliteSnapshotBusinessContentWriter(connection);
    }

    @Override
    public synchronized void putSnapshotContent(SnapshotBusinessContent content) {
        ensureOpen();
        Objects.requireNonNull(content, "content");
        try {
            ProjectSpecificationId projectId = reader.snapshotProject(content.snapshotId())
                    .orElseThrow(() -> new KnowledgeStoreException("snapshot not found: " + content.snapshotId()));
            SpecificationVersionId boundVersion = reader.snapshotVersion(content.snapshotId())
                    .orElseThrow(() -> new KnowledgeStoreException(
                            "snapshot has no specification version binding: " + content.snapshotId()));
            if (!boundVersion.equals(content.specificationVersionId())) {
                throw new KnowledgeStoreException("business content does not match snapshot specification version");
            }
            validateProjectOwnership(content, projectId);

            Optional<SnapshotBusinessContent> existing = reader.find(content.snapshotId());
            if (existing.isPresent()) {
                if (!existing.orElseThrow().equals(content)) {
                    throw new KnowledgeStoreException("snapshot business content collision: " + content.snapshotId());
                }
                return;
            }

            SqliteTransactionRunner.runVoid(
                    connection,
                    "Cannot store snapshot business content " + content.snapshotId(),
                    ignored -> writer.insert(content));
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot store snapshot business content " + content.snapshotId(), exception);
        }
    }

    @Override
    public synchronized Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        try {
            return reader.find(snapshotId);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read snapshot business content " + snapshotId, exception);
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
            throw new KnowledgeStoreException("Cannot close SQLite snapshot business content store", exception);
        }
    }

    /** Rejects every interpolated SQL identifier outside the deliberately tiny internal identifier grammar. */
    static String requireSqlIdentifier(String identifier, String role) {
        Objects.requireNonNull(identifier, role);
        if (!SQL_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    "SQLite " + role + " identifier must match " + SQL_IDENTIFIER.pattern());
        }
        return identifier;
    }

    private void validateProjectOwnership(SnapshotBusinessContent content, ProjectSpecificationId projectId) {
        content.specifications().forEach(specification -> {
            if (!specification.projectId().equals(projectId)) {
                throw new KnowledgeStoreException("specification belongs to another project: " + specification.id());
            }
        });
        content.changes().forEach(change -> {
            if (!change.projectId().equals(projectId)) {
                throw new KnowledgeStoreException("change belongs to another project: " + change.id());
            }
        });
    }

    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new KnowledgeStoreException("SQLite snapshot business content store is closed");
        }
    }
}
