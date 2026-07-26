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
    void migrationsCreateVersionedNormalizedFoundationWithoutGenericJsonPayload() throws Exception {
        Path database = tempDir.resolve("schema.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Constructor applies migrations.
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertEquals(9, new SqliteSchemaManager().currentVersion(connection));
            List<String> expectedTables = List.of(
                    "schema_migrations",
                    "projects",
                    "knowledge_snapshots",
                    "entity_identity_bindings",
                    "specification_versions",
                    "snapshot_specification_versions",
                    "requirement_versions",
                    "traceability_links",
                    "traceability_link_evidence",
                    "snapshot_traceability_links",
                    "snapshot_external_references",
                    "snapshot_external_reference_attributes",
                    "snapshot_external_reference_history",
                    "snapshot_business_content",
                    "snapshot_evidence",
                    "snapshot_specifications",
                    "snapshot_scenarios",
                    "snapshot_scenario_preconditions",
                    "snapshot_changes",
                    "snapshot_change_scope",
                    "snapshot_change_out_of_scope",
                    "snapshot_change_risks",
                    "snapshot_constraints",
                    "snapshot_design_decisions",
                    "snapshot_implementation_tasks",
                    "snapshot_acceptance_criteria",
                    "snapshot_acceptance_verification_evidence",
                    "sync_state",
                    "sync_inventory_entries",
                    "sync_source_archives");
            expectedTables.forEach(table -> assertTrue(tableExistsUnchecked(connection, table), "missing table " + table));
            assertTrue(indexExists(connection, "uq_projects_root"));
            assertTrue(indexExists(connection, "idx_entity_identity_bindings_domain_identity"));
            assertTrue(indexExists(connection, "uq_requirement_versions_current_snapshot_identity"));
            assertTrue(indexExists(connection, "idx_traceability_links_source"));
            assertTrue(indexExists(connection, "idx_traceability_links_target"));
            assertTrue(indexExists(connection, "idx_snapshot_traceability_links_snapshot"));
            assertTrue(indexExists(connection, "idx_snapshot_external_references_owner"));
            assertTrue(indexExists(connection, "idx_snapshot_specifications_snapshot"));
            assertTrue(indexExists(connection, "idx_snapshot_scenarios_snapshot"));
            assertTrue(indexExists(connection, "idx_snapshot_changes_snapshot"));
            assertTrue(indexExists(connection, "idx_snapshot_constraints_change"));
            assertTrue(indexExists(connection, "idx_snapshot_design_decisions_change"));
            assertTrue(indexExists(connection, "idx_snapshot_implementation_tasks_change"));
            assertTrue(indexExists(connection, "idx_snapshot_acceptance_requirement"));
            assertTrue(indexExists(connection, "idx_snapshot_acceptance_change"));
            assertTrue(indexExists(connection, "idx_snapshot_acceptance_status"));
            assertTrue(indexExists(connection, "idx_sync_inventory_entries_project"));
            assertTrue(indexExists(connection, "idx_sync_source_archives_project_time"));

            for (String table : expectedTables) {
                if (!table.equals("schema_migrations")) {
                    assertFalse(columnNames(connection, table).stream()
                            .anyMatch(name -> name.toLowerCase().contains("json")), "JSON-like column in " + table);
                }
            }
        }
    }

    @Test
    void migrationReplayIsIdempotentAndLedgerContainsNineImmutableEntries() throws Exception {
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
                     "SELECT COUNT(*) AS count, MIN(LENGTH(checksum)) AS min_checksum, MAX(LENGTH(checksum)) AS max_checksum FROM schema_migrations")) {
            assertTrue(result.next());
            assertEquals(9, result.getInt("count"));
            assertEquals(64, result.getInt("min_checksum"));
            assertEquals(64, result.getInt("max_checksum"));
        }
    }

    @Test
    void modifiedMigrationHistoryIsRejected() throws Exception {
        Path database = tempDir.resolve("tampered.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Apply the canonical migrations first.
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
            assertEquals(project, reopened.findProjectByRoot(project.rootLocator()).orElseThrow());
            assertEquals(1, reopened.listProjects().size());
            assertEquals(snapshotId, reopened.activeSnapshot(projectId).orElseThrow().id());
            assertEquals(KnowledgeSnapshotState.ACTIVE, reopened.findSnapshot(snapshotId).orElseThrow().state());
        }
    }

    private boolean tableExistsUnchecked(java.sql.Connection connection, String tableName) {
        try {
            return tableExists(connection, tableName);
        } catch (Exception exception) {
            throw new AssertionError(exception);
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

    private boolean indexExists(java.sql.Connection connection, String indexName) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?")) {
            statement.setString(1, indexName);
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
            return names;
        }
    }
}
