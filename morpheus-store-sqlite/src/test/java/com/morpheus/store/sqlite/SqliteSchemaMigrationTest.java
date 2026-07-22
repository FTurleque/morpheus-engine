package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSchemaMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void migrationV1CreatesVersionedNormalizedFoundationWithoutGenericJsonPayload() throws Exception {
        Path database = tempDir.resolve("schema.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Constructor applies migrations.
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertEquals(1, new SqliteSchemaManager().currentVersion(connection));
            assertTrue(tableExists(connection, "schema_migrations"));
            assertTrue(tableExists(connection, "projects"));
            assertTrue(tableExists(connection, "knowledge_snapshots"));

            List<String> projectColumns = columnNames(connection, "projects");
            List<String> snapshotColumns = columnNames(connection, "knowledge_snapshots");
            assertFalse(projectColumns.stream().anyMatch(name -> name.toLowerCase().contains("json")));
            assertFalse(snapshotColumns.stream().anyMatch(name -> name.toLowerCase().contains("json")));
        }
    }

    @Test
    void migrationReplayIsIdempotentAndLedgerContainsOneImmutableV1Entry() throws Exception {
        Path database = tempDir.resolve("replay.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // First application.
        }
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Replay must be a no-op.
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) AS count, MIN(LENGTH(checksum)) AS checksum_length FROM schema_migrations WHERE version = 1")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt("count"));
            assertEquals(64, result.getInt("checksum_length"));
        }
    }

    @Test
    void modifiedMigrationHistoryIsRejected() throws Exception {
        Path database = tempDir.resolve("tampered.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Apply the canonical migration first.
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE schema_migrations SET checksum = 'tampered' WHERE version = 1");
        }

        assertThrows(KnowledgeStoreException.class, () -> {
            try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
                // Opening must fail before the store becomes usable.
            }
        });
    }

    @Test
    void projectAndActiveSnapshotSurviveStoreReopen() {
        Path database = tempDir.resolve("persistence.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        ProjectStoreEntry project = new ProjectStoreEntry(projectId, new SourceLocator("file", "workspace"));
        KnowledgeSnapshotMetadata snapshot = new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("revision-1"),
                Instant.parse("2026-07-22T12:00:00Z"));

        try (var store = new SqliteSpecificationKnowledgeStore(database)) {
            store.putProject(project);
            store.putSnapshot(snapshot);
            store.activateSnapshot(snapshotId, Optional.empty());
        }

        try (var reopened = new SqliteSpecificationKnowledgeStore(database)) {
            assertEquals(project, reopened.findProject(projectId).orElseThrow());
            assertEquals(snapshotId, reopened.activeSnapshot(projectId).orElseThrow().id());
            assertEquals(KnowledgeSnapshotState.ACTIVE, reopened.findSnapshot(snapshotId).orElseThrow().state());
        }
    }

    private boolean tableExists(java.sql.Connection connection, String tableName) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private List<String> columnNames(java.sql.Connection connection, String tableName) throws Exception {
        List<String> names = new ArrayList<>();
        try (var statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (result.next()) {
                names.add(result.getString("name"));
            }
        }
        return names;
    }
}
