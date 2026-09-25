package com.morpheus.store.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A database written before the revision column existed opens, and its rows start at revision 1. */
class SqliteSyncStateRevisionMigrationTest {
    private static final Instant T0 = Instant.parse("2026-09-25T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void migrationNineteenGivesExistingSyncStateRowsRevisionOneAndTheyStayWritable() throws Exception {
        Path database = tempDir.resolve("sync-v18-upgrade.db");
        ProjectSpecificationId project = ProjectSpecificationId.generate();
        try (var core = new SqliteSpecificationKnowledgeStore(database);
             var ignored = new SqliteSyncStateStore(database)) {
            core.putProject(new ProjectStoreEntry(project, SourceLocator.file("workspace")));
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE sync_state DROP COLUMN revision");
            statement.execute("DELETE FROM schema_migrations WHERE version = 19");
            statement.execute("INSERT INTO sync_state(project_id, last_attempt_at, pending_full_rebuild_reason, "
                    + "current_source_count) VALUES ('" + project + "', '" + T0 + "', 'SCAN_INCOMPLETE', 0)");
        }

        try (var store = new SqliteSyncStateStore(database)) {
            var state = store.findSyncState(project).orElseThrow();

            assertEquals(1L, state.revision(), "an existing row is revision 1: revision 0 means no row");
            assertEquals(Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE), state.pendingFullRebuildReason());
            assertEquals(2L, store.recordAttempt(project, 1L, T0.plusSeconds(1),
                    Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE)));
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertEquals(SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION, new SqliteSchemaManager().currentVersion(connection));
        }
    }
}
