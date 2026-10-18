package com.morpheus.store.sqlite;

import com.morpheus.application.composition.CompositionConflict;
import com.morpheus.application.composition.CompositionResolution;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPack;
import com.morpheus.application.policy.PolicyPackCodec;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A database written by a published version is opened by the current store and keeps what it held.
 *
 * <p>For each distinct schema level in {@code released-schema-levels.properties} the test builds a database with
 * exactly the migrations that release shipped, records them in the ledger with their pinned checksums, writes rows in
 * every table that a later migration reads or rewrites, and opens it with the current store. The rows are then read
 * back through the operations of the stores that own them, and one write per constrained family proves the upgraded
 * database is still writable.</p>
 *
 * <p>The limit of the proof: the rows are written by this test, in the shape of the insert statements of the stores at
 * the tags ({@code v1.0.0}, {@code v1.1.0}, {@code v1.2.0}, {@code v1.2.1}); the released program did not write them.
 * The single-migration tests ({@code SqliteSchemaMigrationTest} and its neighbours) replay one migration on a current
 * schema and cover what a baseline cannot, a duplicate sequence or a legacy value.</p>
 *
 * <p>A released level above {@link #ROW_SHAPES_KNOWN_THROUGH} is refused by name: the rows of that level have to be
 * written from the insert statements of its tag before the level is accepted.</p>
 */
class ReleasedSchemaUpgradeTest {
    static final int ROW_SHAPES_KNOWN_THROUGH = 20;

    private static final String LEDGER_SQL = "SELECT version, name, checksum, applied_at FROM schema_migrations ORDER BY version";
    private static final Instant T0 = Instant.parse("2026-06-01T10:00:00Z");
    private static final String SHA256 = "a".repeat(64);

    @TempDir
    Path tempDir;

    @TestFactory
    Stream<DynamicTest> aDatabaseOfEveryReleasedSchemaLevelIsUpgradedWithoutLosingWhatItHeld() {
        return ReleasedSchemaLevels.distinctLevels().stream().map(level -> DynamicTest.dynamicTest(
                "released schema level " + level, () -> upgradeFrom(level)));
    }

    private void upgradeFrom(int level) throws Exception {
        assertTrue(level <= ROW_SHAPES_KNOWN_THROUGH,
                "released level " + level + " has no row shapes: write them from the insert statements of its tag "
                        + "before declaring it (known through level " + ROW_SHAPES_KNOWN_THROUGH + ")");
        assertTrue(level <= SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION,
                "released level " + level + " is above SUPPORTED_SCHEMA_VERSION "
                        + SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION);
        Path database = Files.createDirectory(tempDir.resolve("level-" + level)).resolve("morpheus.db");
        Fixture fixture = new Fixture(level);
        Map<Integer, LedgerRow> baselineLedger;

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            baselineLedger = applyBaseline(connection, level);
            fixture.writeRows(connection);
            assertEquals(level, new SqliteSchemaManager().currentVersion(connection),
                    "the baseline must be at the released level before the upgrade");
        }

        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Opening with the current runtime applies every migration after the released level.
        }

        Map<Integer, LedgerRow> upgradedLedger;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            upgradedLedger = readLedger(connection);
        }
        assertLedgerIsCompleteAndHistoryUnchanged(level, baselineLedger, upgradedLedger);

        readBackThroughTheStores(database, fixture);
        writeThroughTheStores(database, fixture);

        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // A second opening must not replay an applied migration.
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertEquals(upgradedLedger, readLedger(connection), "a second opening must leave the ledger as it was");
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM projects WHERE id = ?", fixture.project.toString()),
                    "a second opening must not duplicate the project");
            assertEquals(2, count(connection,
                    "SELECT COUNT(*) FROM knowledge_snapshots WHERE project_id = ?", fixture.project.toString()),
                    "a second opening must not duplicate the snapshots");
        }
    }

    private static void assertLedgerIsCompleteAndHistoryUnchanged(
            int level, Map<Integer, LedgerRow> baseline, Map<Integer, LedgerRow> upgraded) {
        int current = SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION;
        assertEquals(current, upgraded.size(), "the upgraded ledger must hold one row per supported version");
        assertEquals(current, upgraded.keySet().stream().mapToInt(Integer::intValue).max().orElseThrow(),
                "the upgraded ledger must reach SUPPORTED_SCHEMA_VERSION");
        for (Map.Entry<Integer, LedgerRow> recorded : baseline.entrySet()) {
            assertEquals(recorded.getValue(), upgraded.get(recorded.getKey()),
                    "the ledger row of V" + recorded.getKey() + " written at level " + level + " changed");
            assertEquals(SqliteMigrationChecksumGoldenTest.CANONICAL_CHECKSUMS.get(recorded.getKey()),
                    upgraded.get(recorded.getKey()).checksum(),
                    "the pinned checksum of V" + recorded.getKey() + " is not the one the ledger holds");
        }
    }

    private void readBackThroughTheStores(Path database, Fixture fixture) {
        readBackProjectAndSnapshots(database, fixture);
        readBackSpecificationVersions(database, fixture);
        readBackSyncState(database, fixture);
        readBackComposition(database, fixture);
        if (fixture.hasPolicies()) {
            readBackPolicies(database, fixture);
        }
    }

    private void readBackProjectAndSnapshots(Path database, Fixture fixture) {
        try (var core = new SqliteSpecificationKnowledgeStore(database)) {
            assertEquals(fixture.project, core.findProject(fixture.project).orElseThrow().id(),
                    "SqliteSpecificationKnowledgeStore.findProject lost the project identity");
            assertEquals(2, core.listSnapshots(fixture.project).size(),
                    "SqliteSpecificationKnowledgeStore.listSnapshots lost a snapshot");
            var active = core.activeSnapshot(fixture.project).orElseThrow();
            assertEquals(fixture.activeSnapshot, active.id(),
                    "SqliteSpecificationKnowledgeStore.activeSnapshot returned another snapshot");
            assertEquals(Optional.of(fixture.retiredSnapshot), active.predecessorId(),
                    "SqliteSpecificationKnowledgeStore.activeSnapshot lost the predecessor");
            assertEquals(Optional.of("rev-2"), active.sourceRevision(),
                    "SqliteSpecificationKnowledgeStore.activeSnapshot lost the source revision");
        }
    }

    private void readBackSpecificationVersions(Path database, Fixture fixture) {
        try (var versions = new SqliteVersionedRequirementStore(database)) {
            for (SpecificationVersion expected : fixture.specificationVersions()) {
                assertEquals(Optional.of(expected), versions.findSpecificationVersion(expected.id()),
                        "SqliteVersionedRequirementStore.findSpecificationVersion changed " + expected.id());
            }
            assertEquals(Optional.of(new SnapshotSpecificationVersionBinding(
                            fixture.activeSnapshot, fixture.versionB)),
                    versions.findSnapshotVersion(fixture.activeSnapshot),
                    "SqliteVersionedRequirementStore.findSnapshotVersion lost the binding");
        }
    }

    private void readBackSyncState(Path database, Fixture fixture) {
        try (var sync = new SqliteSyncStateStore(database)) {
            var state = sync.findSyncState(fixture.project).orElseThrow();
            assertEquals(fixture.expectedSyncRevision(), state.revision(),
                    "SqliteSyncStateStore.findSyncState: a row written before the revision column starts at 1");
            assertEquals(Optional.of("rev-2"), state.sourceRevision(),
                    "SqliteSyncStateStore.findSyncState lost the source revision");
            assertEquals(Optional.of(SyncPlan.SyncMode.INCREMENTAL), state.lastSuccessfulMode(),
                    "SqliteSyncStateStore.findSyncState lost the last successful mode");
            assertEquals(1, state.currentSourceCount(),
                    "SqliteSyncStateStore.findSyncState lost the source count");
            assertEquals(1, sync.findCurrentInventory(fixture.project).orElseThrow().entries().size(),
                    "SqliteSyncStateStore.findCurrentInventory lost the inventory entry");
        }
    }

    private void readBackComposition(Path database, Fixture fixture) {
        try (var composition = new SqliteCompositionStateStore(database)) {
            var state = composition.find(fixture.activeSnapshot).orElseThrow();
            assertEquals(new ProviderId("openspec"), state.primaryProviderId(),
                    "SqliteCompositionStateStore.find lost the primary provider");
            assertEquals(2, state.providers().size(), "SqliteCompositionStateStore.find lost a provider state");
            CompositionConflict conflict = state.conflicts().getFirst();
            assertEquals(1, state.conflicts().size(), "SqliteCompositionStateStore.find lost the conflict");
            assertEquals(CompositionResolution.PRECEDENCE_RECORDED, conflict.resolution(),
                    "SqliteCompositionStateStore.find: V020 must rename the stored resolution");
            assertEquals(Optional.of(new ProviderId("openspec")), conflict.selectedProviderId(),
                    "SqliteCompositionStateStore.find lost the selected provider");
            assertEquals(2, conflict.candidates().size(),
                    "SqliteCompositionStateStore.find lost a candidate of the conflict");
        }
    }

    private void readBackPolicies(Path database, Fixture fixture) {
        try (var policies = new SqlitePolicyPackStore(database)) {
            assertEquals(Optional.of(fixture.packDefinition()), policies.findDefinition(fixture.pack),
                    "SqlitePolicyPackStore.findDefinition changed the pack");
            assertEquals(List.of(fixture.packVersion()), policies.listVersions(fixture.pack),
                    "SqlitePolicyPackStore.listVersions changed the pack versions");
            var audit = policies.listAudit(fixture.pack, PageRequest.first(PageRequest.MAX_LIMIT));
            assertEquals(1, audit.totalMatches(), "SqlitePolicyPackStore.listAudit lost the audit record");
            assertEquals(fixture.auditId, audit.items().getFirst().id(),
                    "SqlitePolicyPackStore.listAudit changed the audit identity");
        }
    }

    private void writeThroughTheStores(Path database, Fixture fixture) {
        try (var versions = new SqliteVersionedRequirementStore(database)) {
            long next = versions.nextSpecificationVersionSequence(fixture.project);
            assertEquals(4L, next,
                    "SqliteVersionedRequirementStore.nextSpecificationVersionSequence must follow the highest "
                            + "stored sequence (V017 seeds the reservation)");
            SpecificationVersion written = new SpecificationVersion(
                    SpecificationVersionId.generate(), fixture.project, Optional.of(next), Optional.empty(),
                    Optional.of("rev-4"), T0.plusSeconds(60), Optional.of(fixture.versionC));
            versions.putSpecificationVersion(written);
            assertEquals(Optional.of(written), versions.findSpecificationVersion(written.id()),
                    "SqliteVersionedRequirementStore.putSpecificationVersion: the next sequence must be writable "
                            + "under the unique index of V016");
        }

        try (var sync = new SqliteSyncStateStore(database)) {
            long revision = fixture.expectedSyncRevision();
            assertEquals(revision + 1, sync.recordAttempt(fixture.project, revision, T0.plusSeconds(61),
                            Optional.empty()),
                    "SqliteSyncStateStore.recordAttempt must accept the revision the existing row holds");
        }

        if (fixture.hasPolicies()) {
            try (var policies = new SqlitePolicyPackStore(database)) {
                PolicyPack.Version second = new PolicyPack.Version(
                        fixture.pack, PolicyIds.VersionId.generate(), 2L, "pack v2", List.of(rule()), T0.plusSeconds(62));
                PolicyPack.Definition replacement = new PolicyPack.Definition(
                        fixture.pack, "pack v2", 2L, 2L, T0, T0.plusSeconds(62));
                var audit = new PolicyConfiguration.AuditRecord(
                        DomainIdentity.generate(), PolicyConfiguration.AuditAction.UPDATE, fixture.pack,
                        Optional.of(second.versionId()), Optional.empty(), Optional.empty(), "upgrade-test",
                        "update after upgrade", T0.plusSeconds(62));

                assertEquals(replacement, policies.compareAndSetDefinition(fixture.pack, 1L, replacement, second, audit),
                        "SqlitePolicyPackStore.compareAndSetDefinition must advance a pack written by an earlier "
                                + "release by exactly one revision (trigger of V018)");
                assertEquals(2L, policies.findDefinition(fixture.pack).orElseThrow().revision(),
                        "SqlitePolicyPackStore.findDefinition must see the advanced revision");
                var page = policies.listAudit(fixture.pack, PageRequest.first(PageRequest.MAX_LIMIT));
                assertEquals(List.of(fixture.auditId, audit.id()),
                        page.items().stream().map(PolicyConfiguration.AuditRecord::id).toList(),
                        "SqlitePolicyPackStore.listAudit must order the record written before the upgrade before "
                                + "the one written after (at_key of V021)");
            }
        }
    }

    private static Map<Integer, LedgerRow> applyBaseline(Connection connection, int level) throws Exception {
        List<Path> scripts = migrationScripts(level);
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE schema_migrations (
                        version INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        checksum TEXT NOT NULL,
                        applied_at TEXT NOT NULL
                    )
                    """);
        }
        SqliteSchemaManager manager = new SqliteSchemaManager();
        for (int version = 1; version <= level; version++) {
            Path script = scripts.get(version - 1);
            String file = script.getFileName().toString();
            String name = file.substring(file.indexOf("__") + 2, file.length() - ".sql".length()).replace('_', '-');
            // The ledger a released database holds is a fact of the past: recomputing it from the resources under
            // test would follow any later edit of a released migration instead of catching it.
            String checksum = SqliteMigrationChecksumGoldenTest.CANONICAL_CHECKSUMS.get(version);
            manager.executeScript(connection,
                    Files.readString(script, StandardCharsets.UTF_8).replace("\r\n", "\n"));
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, name, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
                insert.setInt(1, version);
                insert.setString(2, name);
                insert.setString(3, checksum);
                insert.setString(4, T0.toString());
                insert.executeUpdate();
            }
        }
        connection.commit();
        connection.setAutoCommit(true);
        return readLedger(connection);
    }

    private static List<Path> migrationScripts(int level) throws IOException, URISyntaxException {
        var directory = ReleasedSchemaUpgradeTest.class.getResource("/db/migration/");
        assertTrue(directory != null, "the migration resources are not on the test classpath");
        try (Stream<Path> files = Files.list(Path.of(directory.toURI()))) {
            List<Path> scripts = files
                    .filter(path -> path.getFileName().toString().matches("V\\d{3}__.+\\.sql"))
                    .sorted()
                    .toList();
            assertTrue(scripts.size() >= level,
                    "released level " + level + " but only " + scripts.size() + " migrations exist");
            return scripts;
        }
    }

    private static Map<Integer, LedgerRow> readLedger(Connection connection) throws SQLException {
        Map<Integer, LedgerRow> ledger = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(LEDGER_SQL)) {
            while (result.next()) {
                ledger.put(result.getInt("version"), new LedgerRow(
                        result.getString("name"), result.getString("checksum"), result.getString("applied_at")));
            }
        }
        return ledger;
    }

    private static int count(Connection connection, String sql, String parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "No result for: " + sql);
                return result.getInt(1);
            }
        }
    }

    private static PolicyRule rule() {
        return new PolicyRule(
                PolicyIds.RuleId.generate(),
                "upgrade",
                PolicyRule.Kind.LIFECYCLE_GUARD,
                PolicyRule.Severity.WARNING,
                new PolicyRule.LifecycleGuard(
                        ChangeId.generate(), ChangeLifecycleState.PROPOSED, ChangeLifecycleState.SPECIFIED));
    }

    private record LedgerRow(String name, String checksum, String appliedAt) {
    }

    /** The rows a database of the given level holds; each insert follows the store of the tags at that level. */
    private static final class Fixture {
        final int level;
        final ProjectSpecificationId project = ProjectSpecificationId.generate();
        final KnowledgeSnapshotId retiredSnapshot = KnowledgeSnapshotId.generate();
        final KnowledgeSnapshotId activeSnapshot = KnowledgeSnapshotId.generate();
        final SpecificationVersionId versionA = SpecificationVersionId.generate();
        final SpecificationVersionId versionB = SpecificationVersionId.generate();
        final SpecificationVersionId versionC = SpecificationVersionId.generate();
        final PolicyIds.PackId pack = PolicyIds.PackId.generate();
        final PolicyIds.VersionId packVersionId = PolicyIds.VersionId.generate();
        final DomainIdentity auditId = DomainIdentity.generate();
        private final PolicyPack.Version packVersion;

        Fixture(int level) {
            this.level = level;
            this.packVersion = new PolicyPack.Version(pack, packVersionId, 1L, "pack", List.of(rule()), T0);
        }

        boolean hasPolicies() {
            return level >= 15;
        }

        long expectedSyncRevision() {
            return level >= 19 ? 3L : 1L;
        }

        PolicyPack.Definition packDefinition() {
            return new PolicyPack.Definition(pack, "pack", 1L, 1L, T0, T0);
        }

        PolicyPack.Version packVersion() {
            return packVersion;
        }

        List<SpecificationVersion> specificationVersions() {
            return List.of(
                    new SpecificationVersion(versionA, project, Optional.of(1L), Optional.of("openspec-1"),
                            Optional.of("rev-1"), T0, Optional.empty()),
                    new SpecificationVersion(versionB, project, Optional.of(2L), Optional.empty(),
                            Optional.of("rev-2"), T0.plusSeconds(1), Optional.of(versionA)),
                    new SpecificationVersion(versionC, project, Optional.of(3L), Optional.empty(),
                            Optional.of("rev-3"), T0.plusSeconds(2), Optional.of(versionB)));
        }

        void writeRows(Connection connection) throws SQLException {
            writeProjectAndSnapshots(connection);
            writeSpecificationVersions(connection);
            writeSyncState(connection);
            writeComposition(connection);
            if (hasPolicies()) {
                writePolicies(connection);
            }
        }

        private void writeProjectAndSnapshots(Connection connection) throws SQLException {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO projects(id, root_scheme, root_value) VALUES (?, ?, ?)")) {
                insert.setString(1, project.toString());
                insert.setString(2, "file");
                insert.setString(3, "workspace-upgrade");
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO knowledge_snapshots(
                        id, project_id, predecessor_id, state, source_revision, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                snapshot(insert, retiredSnapshot, null, "RETIRED", "rev-1", T0);
                snapshot(insert, activeSnapshot, retiredSnapshot, "ACTIVE", "rev-2", T0.plusSeconds(1));
            }
        }

        private void snapshot(PreparedStatement insert, KnowledgeSnapshotId id, KnowledgeSnapshotId predecessor,
                              String state, String revision, Instant createdAt) throws SQLException {
            insert.setString(1, id.toString());
            insert.setString(2, project.toString());
            if (predecessor == null) {
                insert.setNull(3, Types.VARCHAR);
            } else {
                insert.setString(3, predecessor.toString());
            }
            insert.setString(4, state);
            insert.setString(5, revision);
            insert.setString(6, createdAt.toString());
            insert.executeUpdate();
        }

        private void writeSpecificationVersions(Connection connection) throws SQLException {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO specification_versions(
                        id, project_id, sequence, provider_version, source_revision, created_at, predecessor_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (SpecificationVersion version : specificationVersions()) {
                    insert.setString(1, version.id().toString());
                    insert.setString(2, version.projectId().toString());
                    insert.setLong(3, version.sequence().orElseThrow());
                    insert.setString(4, version.providerVersion().orElse(null));
                    insert.setString(5, version.sourceRevision().orElse(null));
                    insert.setString(6, version.createdAt().toString());
                    insert.setString(7, version.predecessor().map(SpecificationVersionId::toString).orElse(null));
                    insert.executeUpdate();
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO snapshot_specification_versions(snapshot_id, specification_version_id) VALUES (?, ?)")) {
                insert.setString(1, retiredSnapshot.toString());
                insert.setString(2, versionA.toString());
                insert.executeUpdate();
                insert.setString(1, activeSnapshot.toString());
                insert.setString(2, versionB.toString());
                insert.executeUpdate();
            }
            if (level >= 17) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO specification_version_sequences(project_id, last_sequence) VALUES (?, 3)")) {
                    insert.setString(1, project.toString());
                    insert.executeUpdate();
                }
            }
        }

        private void writeSyncState(Connection connection) throws SQLException {
            String columns = "project_id, last_attempt_at, last_successful_sync_at, last_observed_change_at, "
                    + "source_revision, last_successful_mode, pending_full_rebuild_reason, inventory_captured_at, "
                    + "current_source_count";
            String values = "?, ?, ?, ?, ?, 'INCREMENTAL', NULL, ?, 1";
            if (level >= 19) {
                columns += ", revision";
                values += ", 3";
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO sync_state(" + columns + ") VALUES (" + values + ")")) {
                insert.setString(1, project.toString());
                insert.setString(2, T0.plusSeconds(2).toString());
                insert.setString(3, T0.plusSeconds(2).toString());
                insert.setString(4, T0.plusSeconds(1).toString());
                insert.setString(5, "rev-2");
                insert.setString(6, T0.plusSeconds(2).toString());
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO sync_inventory_entries(project_id, source_path, fingerprint_sha256, size_bytes)
                    VALUES (?, 'specs/a.md', ?, 120)
                    """)) {
                insert.setString(1, project.toString());
                insert.setString(2, SHA256);
                insert.executeUpdate();
            }
        }

        private void writeComposition(Connection connection) throws SQLException {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO composition_snapshot_state(snapshot_id, primary_provider_id) VALUES (?, 'openspec')")) {
                insert.setString(1, activeSnapshot.toString());
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO composition_provider_state(
                        snapshot_id, provider_id, priority, required, available, diagnostic_count)
                    VALUES (?, ?, ?, ?, 1, ?)
                    """)) {
                insert.setString(1, activeSnapshot.toString());
                insert.setString(2, "openspec");
                insert.setInt(3, 100);
                insert.setInt(4, 1);
                insert.setInt(5, 0);
                insert.executeUpdate();
                insert.setString(2, "markdown");
                insert.setInt(3, 50);
                insert.setInt(4, 0);
                insert.setInt(5, 2);
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO composition_conflict(
                        conflict_id, snapshot_id, entity_type, logical_key, field_name, resolution,
                        selected_provider_id, reason)
                    VALUES (1, ?, 'REQUIREMENT', 'auth', 'title', ?, 'openspec', 'higher precedence')
                    """)) {
                insert.setString(1, activeSnapshot.toString());
                insert.setString(2, level >= 20 ? "PRECEDENCE_RECORDED" : "SELECTED_BY_PRECEDENCE");
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO composition_conflict_candidate(
                        conflict_id, candidate_order, provider_id, priority, candidate_value, source_locator, evidence_id)
                    VALUES (1, ?, ?, ?, ?, ?, ?)
                    """)) {
                candidate(insert, 0, "openspec", 100, "Login", "specs/a.md", "evidence-1");
                candidate(insert, 1, "markdown", 50, "Sign in", "docs/a.md", "evidence-2");
            }
        }

        private void candidate(PreparedStatement insert, int order, String provider, int priority, String value,
                               String locator, String evidence) throws SQLException {
            insert.setInt(1, order);
            insert.setString(2, provider);
            insert.setInt(3, priority);
            insert.setString(4, value);
            insert.setString(5, locator);
            insert.setString(6, evidence);
            insert.executeUpdate();
        }

        private void writePolicies(Connection connection) throws SQLException {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO policy_packs(id, name, revision, latest_version_number, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, pack.toString());
                insert.setString(2, "pack");
                insert.setLong(3, 1L);
                insert.setLong(4, 1L);
                insert.setString(5, T0.toString());
                insert.setString(6, T0.toString());
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO policy_pack_versions(pack_id, version_id, version_number, encoded_version, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, pack.toString());
                insert.setString(2, packVersionId.toString());
                insert.setLong(3, 1L);
                insert.setString(4, new PolicyPackCodec().encode(packVersion));
                insert.setString(5, T0.toString());
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO policy_audit(
                        id, action, pack_id, version_id, rule_id, scope_kind, scope_id, actor, reason, at)
                    VALUES (?, 'CREATE', ?, ?, NULL, NULL, NULL, 'upgrade-test', 'create', ?)
                    """)) {
                insert.setString(1, auditId.toString());
                insert.setString(2, pack.toString());
                insert.setString(3, packVersionId.toString());
                insert.setString(4, T0.plusNanos(500_100_000L).toString());
                insert.executeUpdate();
            }
        }
    }
}
