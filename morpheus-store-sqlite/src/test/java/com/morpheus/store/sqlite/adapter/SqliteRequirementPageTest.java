package com.morpheus.store.sqlite.adapter;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementPage;
import com.morpheus.application.store.RequirementPageCriteria;
import com.morpheus.application.store.RequirementPageOrder;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.sqlite.runtime.SqliteOperationRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SQLite store cuts the page of CURRENT requirements itself: the match runs through the application's
 * normalization registered as a SQL function, the order compares identities the way the domain does, and the total
 * comes from the same statement as the page.
 */
class SqliteRequirementPageTest {
    private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");
    private static final ProjectSpecificationId PROJECT = new ProjectSpecificationId(identity("0", 1));
    private static final KnowledgeSnapshotId SNAPSHOT = new KnowledgeSnapshotId(identity("0", 2));
    private static final SpecificationVersionId VERSION = new SpecificationVersionId(identity("0", 3));
    private static final SpecificationId SPEC_A = new SpecificationId(identity("0", 4));
    private static final SpecificationId SPEC_B = new SpecificationId(identity("0", 5));

    @TempDir
    Path tempDir;

    @Test
    void theStoreMatchesWithTheApplicationsFoldingAndOrdersIdentitiesLikeTheDomain() {
        Path database = tempDir.resolve("page.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            List<RequirementVersionRecord> current = seed(snapshots, requirements);
            RequirementVersionRecord school = current.get(0);
            RequirementVersionRecord percent = current.get(1);
            RequirementVersionRecord negative = current.get(2);

            assertEquals(new RequirementPage(List.of(school), 1), requirements.pageCurrentRequirementVersions(
                    SNAPSHOT, RequirementPageCriteria.lexical(List.of("école")), RequirementPageOrder.REQUIREMENT_ID,
                    0, 10));
            assertEquals(new RequirementPage(List.of(percent), 1), requirements.pageCurrentRequirementVersions(
                    SNAPSHOT, RequirementPageCriteria.lexical(List.of("%")), RequirementPageOrder.REQUIREMENT_ID, 0, 10));
            assertEquals(new RequirementPage(List.of(negative, school, percent), 3),
                    requirements.pageCurrentRequirementVersions(SNAPSHOT, RequirementPageCriteria.all(),
                            RequirementPageOrder.ENTITY_IDENTITY, 0, 10),
                    "an identity whose text starts with f holds a negative most significant half and comes first");
            assertEquals(new RequirementPage(List.of(percent), 2), requirements.pageCurrentRequirementVersions(
                    SNAPSHOT, RequirementPageCriteria.ofSpecification(SPEC_A), RequirementPageOrder.REQUIREMENT_ID,
                    1, 10));
            assertEquals(new RequirementPage(List.of(), 3), requirements.pageCurrentRequirementVersions(
                    SNAPSHOT, RequirementPageCriteria.all(), RequirementPageOrder.REQUIREMENT_ID, 3, 10),
                    "a page past the matches still carries their total");
            assertEquals(3, requirements.countCurrentRequirementVersions(SNAPSHOT));
            assertEquals(0, requirements.countCurrentRequirementVersions(new KnowledgeSnapshotId(identity("0", 77))));
        }
    }

    @Test
    void theCorpusFunctionIsRegisteredOnTheConnectionAnOperationScopeLends() {
        Path database = tempDir.resolve("scoped.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            seed(snapshots, requirements);
        }

        OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), new OperationalMetrics());
        try (SqliteOperationRuntime runtime = SqliteOperationRuntime.open(database, recorder)) {
            VersionedRequirementStore requirements = runtime.requirements();
            RequirementPage page = requirements.pageCurrentRequirementVersions(SNAPSHOT,
                    RequirementPageCriteria.lexical(List.of("élèves")), RequirementPageOrder.REQUIREMENT_ID, 0, 10);
            assertEquals(1, page.totalMatches());
            assertEquals("REQ-SCHOOL", page.items().getFirst().entityVersion().content().key().orElseThrow());
        }
    }

    @Test
    void theCorpusFunctionComputesTheApplicationsCorpus() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            SqliteVersionedRequirementStore.registerCorpusFunction(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + SqliteVersionedRequirementStore.CORPUS_FUNCTION + "(?, ?, ?)")) {
                statement.setString(1, null);
                statement.setString(2, "ÉCOLE");
                statement.setString(3, "100% Élèves");
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(RequirementPageCriteria.corpus(Optional.empty(), "ÉCOLE", "100% Élèves"),
                            result.getString(1));
                }
            }
        }
    }

    @Test
    void theRowsADatabaseHeldBeforeTheOrderKeysAreReadInThePageOrderOnceMigrated() throws Exception {
        Path database = tempDir.resolve("v23-upgrade.db");
        List<RequirementVersionRecord> current;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            current = seed(snapshots, requirements);
        }
        String url = "jdbc:sqlite:" + database.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            // step back over V024 only: the rows stay, written as an earlier version wrote them
            statement.execute("DROP INDEX idx_requirement_versions_current_by_requirement");
            statement.execute("DROP INDEX idx_requirement_versions_current_by_identity");
            statement.execute("ALTER TABLE requirement_versions DROP COLUMN requirement_order_key");
            statement.execute("ALTER TABLE requirement_versions DROP COLUMN identity_order_key");
            statement.execute("ALTER TABLE requirement_versions DROP COLUMN version_order_key");
            statement.execute("DELETE FROM schema_migrations WHERE version = 24");
        }

        try (var requirements = new SqliteVersionedRequirementStore(database)) {
            assertEquals(new RequirementPage(List.of(current.get(2), current.get(0), current.get(1)), 3),
                    requirements.pageCurrentRequirementVersions(SNAPSHOT, RequirementPageCriteria.all(),
                            RequirementPageOrder.ENTITY_IDENTITY, 0, 10),
                    "the migration keys the rows it finds, so they are read in the signed order of their identities");
            assertEquals(new RequirementPage(List.of(current.get(0)), 3),
                    requirements.pageCurrentRequirementVersions(SNAPSHOT, RequirementPageCriteria.all(),
                            RequirementPageOrder.REQUIREMENT_ID, 1, 1));
        }
        try (Connection connection = DriverManager.getConnection(url);
             var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT requirement_id, requirement_order_key, entity_version_id, "
                     + "version_order_key FROM requirement_versions ORDER BY entity_version_id")) {
            int seen = 0;
            while (rows.next()) {
                assertEquals(signedKey(rows.getString("requirement_id")), rows.getString("requirement_order_key"));
                assertEquals(signedKey(rows.getString("entity_version_id")), rows.getString("version_order_key"));
                seen++;
            }
            assertEquals(4, seen, "every row is keyed, the proposed one included");
        }
    }

    @Test
    void aSnapshotWithoutARowReadsAsAnEmptyPageOfNone() {
        Path database = tempDir.resolve("unknown-snapshot.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            seed(snapshots, requirements);

            assertEquals(new RequirementPage(List.of(), 0), requirements.pageCurrentRequirementVersions(
                    new KnowledgeSnapshotId(identity("0", 77)), RequirementPageCriteria.all(),
                    RequirementPageOrder.REQUIREMENT_ID, 0, 10));
        }
    }

    private static String signedKey(String identity) {
        return (identity.compareTo("8") >= 0 ? "0" : "1") + identity;
    }

    @Test
    void aClosedStoreRefusesThePageAndTheCount() {
        Path database = tempDir.resolve("closed.db");
        SqliteVersionedRequirementStore requirements = new SqliteVersionedRequirementStore(database);
        requirements.close();

        KnowledgeStoreException page = assertThrows(KnowledgeStoreException.class,
                () -> requirements.pageCurrentRequirementVersions(
                        SNAPSHOT, RequirementPageCriteria.all(), RequirementPageOrder.REQUIREMENT_ID, 0, 1));
        assertEquals("SQLite versioned requirement store is closed", page.getMessage());
        KnowledgeStoreException count = assertThrows(KnowledgeStoreException.class,
                () -> requirements.countCurrentRequirementVersions(SNAPSHOT));
        assertEquals("SQLite versioned requirement store is closed", count.getMessage());
    }

    private static List<RequirementVersionRecord> seed(
            SqliteSpecificationKnowledgeStore snapshots, SqliteVersionedRequirementStore requirements) {
        snapshots.putProject(new ProjectStoreEntry(PROJECT, SourceLocator.file("workspace-page")));
        requirements.putSpecificationVersion(new SpecificationVersion(VERSION, PROJECT, Optional.of(1L),
                Optional.of("provider-v1"), Optional.of("revision-1"), T0, Optional.empty()));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(SNAPSHOT, PROJECT, Optional.empty(),
                KnowledgeSnapshotState.READY, Optional.of("revision-1"), T0));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(SNAPSHOT, VERSION));
        List<RequirementVersionRecord> current = List.of(
                record("1", 10, SPEC_A, "REQ-SCHOOL", "ÉCOLE PUBLIQUE", "Les élèves", TemporalState.CURRENT),
                record("2", 11, SPEC_A, "REQ-PERCENT", "Coverage", "Reach 100% coverage", TemporalState.CURRENT),
                record("f", 12, SPEC_B, "REQ-NEGATIVE", "Negative half", "Reach 100 coverage", TemporalState.CURRENT));
        current.forEach(requirements::putRequirementVersion);
        requirements.putRequirementVersion(
                record("3", 13, SPEC_A, "REQ-PROPOSED", "École proposée", "Proposed 100%", TemporalState.PROPOSED));
        snapshots.activateSnapshot(SNAPSHOT, Optional.empty());
        return current;
    }

    private static RequirementVersionRecord record(
            String digit, int number, SpecificationId spec, String key, String title, String statement,
            TemporalState state) {
        RequirementId id = new RequirementId(identity(digit, number));
        Requirement requirement = new Requirement(id, spec, Optional.of(key), title, statement, new Provenance(
                new ProviderId("sqlite-page"), Optional.empty(), SourceLocator.file("specs/page.md"), Optional.empty(),
                Optional.empty(), new EvidenceId(identity("0", 99))));
        return new RequirementVersionRecord(SNAPSHOT, new EntityVersion<>(
                new EntityVersionId(identity("0", 100 + number)), id.value(), VERSION, state, requirement));
    }

    private static DomainIdentity identity(String leadingDigit, int number) {
        return DomainIdentity.parse(leadingDigit + "0000000-0000-7000-8000-%012d".formatted(number));
    }
}
