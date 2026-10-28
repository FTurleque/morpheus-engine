package com.morpheus.store.sqlite.adapter;

import com.morpheus.application.query.PageRequest;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.sync.SourceArchivePage;
import com.morpheus.application.sync.SourceArchiveRecord;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Migration 23 gives every source archive that existed before it the order key the store now writes, so archives
 * written by an earlier version are read in pages in the same order as archives written by this one (AUD-PRF-04).
 */
class SqliteSourceArchiveOrderKeyMigrationTest {
    /** Texts as {@code Instant.toString()} writes them: no fraction, three, six and nine digits. */
    private static final List<String> INSTANTS = List.of(
            "2026-10-09T14:30:00Z",
            "2026-10-09T14:30:00.500Z",
            "2026-10-09T14:30:00.500100Z",
            "2026-10-09T14:30:00.000000001Z",
            "2026-10-09T14:30:00.999999999Z",
            "2026-10-09T14:30:01Z",
            "1970-01-01T00:00:00Z",
            "2099-12-31T23:59:59.123456Z");
    private static final String SHA256 = "b".repeat(64);

    @TempDir
    Path tempDir;

    @Test
    void anArchiveThatExistedBeforeTheMigrationGetsTheKeyAndIsReadInTimeOrder() throws Exception {
        Path database = tempDir.resolve("v22-upgrade.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        try (var core = new SqliteSpecificationKnowledgeStore(database)) {
            core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        }
        String url = "jdbc:sqlite:" + database.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            // step back over V023 only
            statement.execute("DROP INDEX idx_sync_source_archives_project_order");
            statement.execute("ALTER TABLE sync_source_archives DROP COLUMN archived_key");
            statement.execute("CREATE INDEX idx_sync_source_archives_project_time "
                    + "ON sync_source_archives(project_id, archived_at)");
            statement.execute("DELETE FROM schema_migrations WHERE version = 23");
        }
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO sync_source_archives(project_id, source_path, fingerprint_sha256, size_bytes, "
                             + "archived_at, reason) VALUES (?, ?, ?, 1, ?, 'DELETED')")) {
            for (int index = 0; index < INSTANTS.size(); index++) {
                insert.setString(1, projectId.toString());
                insert.setString(2, "spec/archive-" + index + ".md");
                insert.setString(3, SHA256);
                insert.setString(4, INSTANTS.get(index));
                insert.executeUpdate();
            }
        }

        List<SourceArchiveRecord> paged = new ArrayList<>();
        try (var store = new SqliteSyncStateStore(database)) {
            for (int offset = 0; offset < INSTANTS.size(); offset += 3) {
                SourceArchivePage page = store.listArchives(projectId, new PageRequest(offset, 3));
                assertEquals(INSTANTS.size(), page.totalMatches());
                paged.addAll(page.items());
            }
        }

        assertEquals(INSTANTS.stream().map(Instant::parse).sorted().toList(),
                paged.stream().map(SourceArchiveRecord::archivedAt).toList(),
                "the archives an earlier version wrote are read in time order once the migration keyed them");
        Map<String, String> keys = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT archived_at, archived_key FROM sync_source_archives ORDER BY source_path")) {
            while (rows.next()) {
                keys.put(rows.getString("archived_at"), rows.getString("archived_key"));
            }
        }
        assertEquals(INSTANTS, List.copyOf(keys.keySet()), "the stored text of each instant is unchanged");
        for (String instant : INSTANTS) {
            assertNotNull(keys.get(instant), instant);
            assertEquals(SqliteSyncStateStore.archivedKey(Instant.parse(instant)), keys.get(instant), instant);
        }
    }

    @Test
    void theKeysSortAsTheInstantsDo() {
        List<Instant> chronological = INSTANTS.stream().map(Instant::parse).sorted().toList();
        List<String> keyed = chronological.stream().map(SqliteSyncStateStore::archivedKey).toList();

        assertEquals(keyed.stream().sorted().toList(), keyed, "text order of the keys is the order of the instants");
        assertEquals(keyed.size(), keyed.stream().distinct().count());
    }

    @Test
    void theArchiveKeyHasTheFormatOfThePolicyAuditKey() {
        for (String instant : INSTANTS) {
            assertEquals(SqlitePolicyPackStore.orderKey(Instant.parse(instant)),
                    SqliteSyncStateStore.archivedKey(Instant.parse(instant)), instant);
        }
    }
}
