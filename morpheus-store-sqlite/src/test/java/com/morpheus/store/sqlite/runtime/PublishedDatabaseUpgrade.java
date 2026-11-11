package com.morpheus.store.sqlite.runtime;

import com.morpheus.application.composition.CompositionConflict;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.query.PageRequest;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.store.sqlite.adapter.SqliteCompositionStateStore;
import com.morpheus.store.sqlite.adapter.SqlitePolicyPackStore;
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteSyncStateStore;
import com.morpheus.store.sqlite.adapter.SqliteVersionedRequirementStore;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Upgrades a database that a published program wrote and proves it keeps what it held.
 *
 * <p>Every row of every table the published level had is read before the upgrade, and found again afterwards with the
 * same value in every column that level had. The one value a later migration rewrites on purpose is named in
 * {@link #REWRITTEN_VALUES} and expected in its rewritten form. The identities are then read back through the stores
 * that own them, and the history (the retired snapshots, the specification versions, the policy pack revisions and
 * their audit) is checked in its order.</p>
 */
final class PublishedDatabaseUpgrade {
    /** table.column -> {value written by an earlier release, value a later migration rewrites it to}. */
    static final Map<String, List<String>> REWRITTEN_VALUES = Map.of(
            "composition_conflict.resolution", List.of("SELECTED_BY_PRECEDENCE", "PRECEDENCE_RECORDED"));

    private static final List<String> NOT_DATA = List.of("schema_migrations", "sqlite_sequence");

    private PublishedDatabaseUpgrade() {
    }

    static void upgradeAndVerify(int level, Path directory) throws Exception {
        PublishedDatabases.Provenance provenance = PublishedDatabases.provenance(level);
        assertEquals(Optional.of(level), Optional.ofNullable(ReleasedSchemaLevels.byVersion().get(provenance.version())),
                "published-databases/level-" + level + " was written by " + provenance.version()
                        + ", whose declared schema level is not " + level);
        Path database = PublishedDatabases.copy(level, directory);

        Map<String, List<String>> columnsBefore;
        Map<String, List<String>> rowsBefore;
        List<String> ledgerBefore;
        try (Connection connection = open(database)) {
            assertEquals(level, new SqliteSchemaManager().currentVersion(connection),
                    "the database " + provenance.tag() + " wrote is not at level " + level);
            columnsBefore = dataColumns(connection);
            rowsBefore = rows(connection, columnsBefore, true);
            ledgerBefore = ledger(connection);
        }
        History history = History.read(database, columnsBefore);

        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Opening with the current runtime applies every migration after the published level.
        }

        List<String> ledgerAfter;
        try (Connection connection = open(database)) {
            ledgerAfter = ledger(connection);
            assertEquals(SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION, ledgerAfter.size(),
                    "the upgraded ledger must hold one row per supported version");
            assertEquals(ledgerBefore, ledgerAfter.subList(0, ledgerBefore.size()),
                    "the ledger rows " + provenance.tag() + " wrote changed during the upgrade");
            Map<String, List<String>> rowsAfter = rows(connection, columnsBefore, false);
            for (Map.Entry<String, List<String>> table : rowsBefore.entrySet()) {
                List<String> lost = new ArrayList<>(table.getValue());
                rowsAfter.get(table.getKey()).forEach(lost::remove);
                assertEquals(List.of(), lost, "rows of " + table.getKey() + " that " + provenance.tag()
                        + " wrote and the upgrade lost or changed");
                assertEquals(table.getValue().size(), rowsAfter.get(table.getKey()).size(),
                        "the upgrade added rows to " + table.getKey());
            }
        }

        readBackThroughTheStores(database, history, level);

        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // A second opening must not replay an applied migration.
        }
        try (Connection connection = open(database)) {
            assertEquals(ledgerAfter, ledger(connection), "a second opening must leave the ledger as it was");
        }
    }

    private static void readBackThroughTheStores(Path database, History history, int level) {
        try (var core = new SqliteSpecificationKnowledgeStore(database)) {
            assertEquals(history.project, core.findProject(history.project).orElseThrow().id(),
                    "SqliteSpecificationKnowledgeStore.findProject lost the project identity");
            assertEquals(history.snapshotsInOrder.size(), core.listSnapshots(history.project).size(),
                    "SqliteSpecificationKnowledgeStore.listSnapshots lost a snapshot");
            var active = core.activeSnapshot(history.project).orElseThrow();
            assertEquals(history.snapshotsInOrder.getLast(), active.id(),
                    "SqliteSpecificationKnowledgeStore.activeSnapshot returned another snapshot");
            assertEquals(Optional.of(history.snapshotsInOrder.get(history.snapshotsInOrder.size() - 2)),
                    active.predecessorId(), "SqliteSpecificationKnowledgeStore.activeSnapshot lost the predecessor");
        }

        try (var versions = new SqliteVersionedRequirementStore(database)) {
            assertEquals(history.highestSpecificationSequence + 1,
                    versions.nextSpecificationVersionSequence(history.project),
                    "SqliteVersionedRequirementStore.nextSpecificationVersionSequence must follow the highest "
                            + "sequence the published program stored");
        }

        try (var sync = new SqliteSyncStateStore(database)) {
            var state = sync.findSyncState(history.project).orElseThrow();
            assertEquals(history.syncRevision, state.revision(),
                    "SqliteSyncStateStore.findSyncState: a row written before the revision column starts at 1");
            assertEquals(history.syncSourceRevision, state.sourceRevision(),
                    "SqliteSyncStateStore.findSyncState lost the source revision");
        }

        try (var composition = new SqliteCompositionStateStore(database)) {
            List<CompositionConflict> conflicts =
                    composition.find(history.snapshotsInOrder.getLast()).orElseThrow().conflicts();
            assertEquals(history.conflicts, conflicts.size(), "SqliteCompositionStateStore.find lost a conflict");
        }

        assertEquals(level >= 15, history.pack.isPresent(),
                "a database of level " + level + " must hold a policy pack exactly when its level has the tables");
        history.pack.ifPresent(pack -> readBackPolicies(database, history, pack));
    }

    private static void readBackPolicies(Path database, History history, PolicyIds.PackId pack) {
        try (var policies = new SqlitePolicyPackStore(database)) {
            assertEquals(history.packRevision, policies.findDefinition(pack).orElseThrow().revision(),
                    "SqlitePolicyPackStore.findDefinition changed the pack revision");
            assertEquals(history.packRevision, (long) policies.listVersions(pack).size(),
                    "SqlitePolicyPackStore.listVersions lost a pack version");
            var audit = policies.listAudit(pack, PageRequest.first(PageRequest.MAX_LIMIT));
            assertEquals(history.auditInOrder,
                    audit.items().stream().map(PolicyConfiguration.AuditRecord::id).map(Object::toString).toList(),
                    "SqlitePolicyPackStore.listAudit must keep every record the published program wrote, in the "
                            + "order it wrote them");
        }
    }

    private static Connection open(Path database) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
    }

    private static Map<String, List<String>> dataColumns(Connection connection) throws SQLException {
        Map<String, List<String>> columns = new TreeMap<>();
        for (String table : strings(connection,
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")) {
            if (!NOT_DATA.contains(table)) {
                columns.put(table, strings(connection, "SELECT name FROM pragma_table_info('" + table + "') ORDER BY cid"));
            }
        }
        assertFalse(columns.isEmpty(), "the published database holds no table");
        return columns;
    }

    /** Every row of every table, rendered on the given columns and sorted: a row is compared, never its position. */
    private static Map<String, List<String>> rows(
            Connection connection, Map<String, List<String>> columns, boolean rewrite) throws SQLException {
        Map<String, List<String>> rows = new TreeMap<>();
        for (Map.Entry<String, List<String>> table : columns.entrySet()) {
            List<String> rendered = new ArrayList<>();
            String select = "SELECT " + String.join(", ", table.getValue().stream().map(c -> '"' + c + '"').toList())
                    + " FROM \"" + table.getKey() + "\"";
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(select)) {
                while (result.next()) {
                    rendered.add(row(result, table.getKey(), table.getValue(), rewrite));
                }
            }
            rendered.sort(null);
            rows.put(table.getKey(), rendered);
        }
        return rows;
    }

    private static String row(ResultSet result, String table, List<String> columns, boolean rewrite)
            throws SQLException {
        StringBuilder row = new StringBuilder();
        for (int index = 0; index < columns.size(); index++) {
            String value = result.getString(index + 1);
            List<String> rewritten = REWRITTEN_VALUES.get(table + "." + columns.get(index));
            if (rewrite && rewritten != null && rewritten.getFirst().equals(value)) {
                value = rewritten.getLast();
            }
            row.append(columns.get(index)).append('=').append(value).append('\u001f');
        }
        return row.toString();
    }

    private static List<String> ledger(Connection connection) throws SQLException {
        return strings(connection,
                "SELECT version || '|' || name || '|' || checksum || '|' || applied_at FROM schema_migrations ORDER BY version");
    }

    private static List<String> strings(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                values.add(result.getString(1));
            }
        }
        return values;
    }

    /** What the published program recorded, read with SQL before the upgrade and asked of the stores after it. */
    private record History(
            ProjectSpecificationId project,
            List<KnowledgeSnapshotId> snapshotsInOrder,
            long highestSpecificationSequence,
            long syncRevision,
            Optional<String> syncSourceRevision,
            int conflicts,
            Optional<PolicyIds.PackId> pack,
            long packRevision,
            List<String> auditInOrder) {

        static History read(Path database, Map<String, List<String>> columns) throws SQLException {
            try (Connection connection = open(database)) {
                List<String> projects = strings(connection, "SELECT id FROM projects");
                assertEquals(1, projects.size(), "the published database must hold exactly one project");
                ProjectSpecificationId project = ProjectSpecificationId.parse(projects.getFirst());
                List<KnowledgeSnapshotId> snapshots = strings(connection,
                        "SELECT id FROM knowledge_snapshots ORDER BY created_at, id").stream()
                        .map(KnowledgeSnapshotId::parse).toList();
                assertTrue(snapshots.size() >= 2, "the published database must hold a retired and an active snapshot");
                long sequence = Long.parseLong(strings(connection,
                        "SELECT COALESCE(MAX(sequence), 0) FROM specification_versions").getFirst());
                assertTrue(sequence >= 2, "the published database must hold at least two specification versions");
                long revision = columns.get("sync_state").contains("revision")
                        ? Long.parseLong(strings(connection, "SELECT revision FROM sync_state").getFirst())
                        : 1L;
                Optional<String> sourceRevision = Optional.ofNullable(
                        strings(connection, "SELECT source_revision FROM sync_state").getFirst());
                int conflicts = Integer.parseInt(strings(connection,
                        "SELECT COUNT(*) FROM composition_conflict").getFirst());
                assertTrue(conflicts > 0, "the published database must hold composition conflicts");
                if (!columns.containsKey("policy_packs")) {
                    return new History(project, snapshots, sequence, revision, sourceRevision, conflicts,
                            Optional.empty(), 0L, List.of());
                }
                List<String> packs = strings(connection, "SELECT id || '|' || revision FROM policy_packs");
                assertEquals(1, packs.size(), "the published database must hold exactly one policy pack");
                String[] pack = packs.getFirst().split("\\|");
                List<String> audit = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT id FROM policy_audit WHERE pack_id = ? ORDER BY at, id")) {
                    select.setString(1, pack[0]);
                    try (ResultSet result = select.executeQuery()) {
                        while (result.next()) {
                            audit.add(result.getString(1));
                        }
                    }
                }
                assertTrue(Long.parseLong(pack[1]) >= 2 && audit.size() >= 2,
                        "the published policy pack must have been updated once, so its history has two entries");
                return new History(project, snapshots, sequence, revision, sourceRevision, conflicts,
                        Optional.of(PolicyIds.PackId.parse(pack[0])), Long.parseLong(pack[1]), audit);
            }
        }
    }
}
