package com.morpheus.store.sqlite;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteSpecificationVersionSequenceMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void migrationSeventeenBackfillsReservationFromHighestStoredSequence() throws Exception {
        Path database = tempDir.resolve("sequence-v17-upgrade.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Build the current schema, then downgrade only the V017 addition for a deterministic fixture.
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE specification_version_sequences");
            statement.execute("DELETE FROM schema_migrations WHERE version = 17");
            statement.execute("INSERT INTO projects(id, root_scheme, root_value) VALUES ('project-v17', 'file', 'fixture')");
            statement.execute("""
                    INSERT INTO specification_versions(
                        id, project_id, sequence, provider_version, source_revision, created_at, predecessor_id)
                    VALUES
                        ('version-v17-a', 'project-v17', 2, NULL, 'a', '2026-08-30T20:00:00Z', NULL),
                        ('version-v17-b', 'project-v17', 5, NULL, 'b', '2026-08-30T20:00:01Z', 'version-v17-a')
                    """);
        }

        try (var store = new SqliteVersionedRequirementStore(database)) {
            assertEquals(6L, store.nextSpecificationVersionSequence(ProjectSpecificationId.parse("project-v17")));
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT last_sequence
                     FROM specification_version_sequences
                     WHERE project_id = 'project-v17'
                     """)) {
            assertEquals(true, result.next());
            assertEquals(6L, result.getLong(1));
            assertEquals(17, new SqliteSchemaManager().currentVersion(connection));
        }
    }
}
