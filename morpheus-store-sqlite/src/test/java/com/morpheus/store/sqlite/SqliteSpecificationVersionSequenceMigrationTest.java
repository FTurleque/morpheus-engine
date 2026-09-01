package com.morpheus.store.sqlite;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSpecificationVersionSequenceMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void migrationSeventeenBackfillsReservationFromHighestStoredSequence() throws Exception {
        Path database = tempDir.resolve("sequence-v17-upgrade.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Build the current schema, then downgrade only the V017 addition for a deterministic fixture.
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE specification_version_sequences");
            statement.execute("DELETE FROM schema_migrations WHERE version = 17");
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var project = connection.prepareStatement(
                     "INSERT INTO projects(id, root_scheme, root_value) VALUES (?, 'file', 'fixture')");
             var versions = connection.prepareStatement("""
                     INSERT INTO specification_versions(
                         id, project_id, sequence, provider_version, source_revision, created_at, predecessor_id)
                     VALUES (?, ?, ?, NULL, ?, ?, ?)
                     """)) {
            project.setString(1, projectId.toString());
            project.executeUpdate();

            versions.setString(1, "version-v17-a");
            versions.setString(2, projectId.toString());
            versions.setLong(3, 2L);
            versions.setString(4, "a");
            versions.setString(5, "2026-08-30T20:00:00Z");
            versions.setString(6, null);
            versions.executeUpdate();

            versions.setString(1, "version-v17-b");
            versions.setString(2, projectId.toString());
            versions.setLong(3, 5L);
            versions.setString(4, "b");
            versions.setString(5, "2026-08-30T20:00:01Z");
            versions.setString(6, "version-v17-a");
            versions.executeUpdate();
        }

        try (var store = new SqliteVersionedRequirementStore(database)) {
            assertEquals(6L, store.nextSpecificationVersionSequence(projectId));
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var query = connection.prepareStatement("""
                     SELECT last_sequence
                     FROM specification_version_sequences
                     WHERE project_id = ?
                     """)) {
            query.setString(1, projectId.toString());
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals(6L, result.getLong(1));
            }
            assertEquals(17, new SqliteSchemaManager().currentVersion(connection));
        }
    }
}
