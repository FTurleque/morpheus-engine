package com.morpheus.architecture;

import com.morpheus.application.history.HistoricalRequirementQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.RequirementQueryService;
import com.morpheus.application.query.RequirementSearchPage;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.query.SpecificationContextQueryService;
import com.morpheus.application.query.SpecificationContextResult;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementPage;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.evidence.Evidence;
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
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.adapter.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.adapter.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what the three paged requirement reads return - the lexical search, the requirements of one specification and
 * the requirements of one published version - on the memory store and on the SQLite store, over requirements that
 * hold what a naive SQL translation of the match gets wrong: upper-case accented and non-Latin letters, the SQL
 * wildcards {@code %} and {@code _}, a backslash, a requirement without a key, terms found in two different fields,
 * and a term that would only match across the boundary of two fields. The identities are chosen so that the text order
 * of their canonical form and the order of {@link java.util.UUID#compareTo} disagree: half of them carry a most
 * significant half whose top bit is set.
 *
 * <p>The expected values are written out: this suite is the definition of what the reads return, whichever store
 * evaluates the criteria, the order and the page.</p>
 */
class RequirementPageReadsCharacterisationTest {
    private static final Instant T0 = Instant.parse("2026-10-29T08:00:00Z");
    private static final ProjectSpecificationId PROJECT = new ProjectSpecificationId(identity("0", 900));
    private static final KnowledgeSnapshotId ACTIVE = new KnowledgeSnapshotId(identity("0", 901));
    private static final KnowledgeSnapshotId READY = new KnowledgeSnapshotId(identity("0", 902));
    private static final SpecificationVersionId ACTIVE_VERSION = new SpecificationVersionId(identity("0", 903));
    private static final SpecificationVersionId READY_VERSION = new SpecificationVersionId(identity("0", 904));
    private static final SpecificationId SPEC_A = new SpecificationId(identity("0", 905));
    private static final SpecificationId SPEC_B = new SpecificationId(identity("0", 906));
    private static final Evidence EVIDENCE = new Evidence(
            new EvidenceId(identity("0", 907)), SourceLocator.file("specs/page.md"), Optional.empty(),
            Optional.of("sha256:page"));
    private static final Provenance PROVENANCE = new Provenance(
            new ProviderId("page-fixture"), Optional.of("1"), EVIDENCE.source(), Optional.empty(),
            Optional.of("revision-1"), EVIDENCE.id());
    private static final ChangeProposal CHANGE_ON_A = change(910, "CHG-A");
    private static final ChangeProposal CHANGE_ON_B = change(911, "CHG-B");

    /** Requirement identity digit, key, title, statement, specification - in the order they are written. */
    private static final List<Row> ROWS = List.of(
            new Row("1", 1, "REQ-SCHOOL", "ÉCOLE PUBLIQUE", "Les élèves sont inscrits", SPEC_A),
            new Row("f", 2, "REQ-OMEGA", "ΣΥΣΤΗΜΑ Ωμέγα", "Le système grec", SPEC_A),
            new Row("2", 3, "REQ-PERCENT", "Coverage target", "Reach 100% coverage", SPEC_A),
            new Row("9", 4, "REQ-PLAIN", "Coverage plain", "Reach 100 coverage", SPEC_A),
            new Row("3", 5, "snake_case", "Underscore key", "A key with an underscore", SPEC_B),
            new Row("a", 6, "snakeXcase", "Letter key", "A key with a letter", SPEC_B),
            new Row("4", 7, "REQ-PATH", "Backslash path", "Store under C:\\data", SPEC_B),
            new Row("8", 8, null, "Keyless requirement", "Has no key at all", SPEC_A),
            new Row("5", 9, "ALPHA-9", "beta gamma", "delta", SPEC_B));

    @TempDir
    Path tempDir;

    private final List<AutoCloseable> opened = new ArrayList<>();

    @AfterEach
    void closeTheSqliteStores() throws Exception {
        for (AutoCloseable store : opened.reversed()) {
            store.close();
        }
    }

    @Test
    void theLexicalSearchFoldsEveryLetterTheApplicationFoldsAndOnlyThose() {
        for (Backend backend : backends()) {
            assertSearch(backend, "école", 0, 10, List.of("REQ-SCHOOL"), 1);
            assertSearch(backend, "ÉLÈVES", 0, 10, List.of("REQ-SCHOOL"), 1);
            assertSearch(backend, "συστημα", 0, 10, List.of("REQ-OMEGA"), 1);
            assertSearch(backend, "ωμέγα", 0, 10, List.of("REQ-OMEGA"), 1);
            assertSearch(backend, "ecole", 0, 10, List.of(), 0);
        }
    }

    @Test
    void aSqlWildcardOrABackslashMatchesOnlyItself() {
        for (Backend backend : backends()) {
            assertSearch(backend, "%", 0, 10, List.of("REQ-PERCENT"), 1);
            assertSearch(backend, "100%", 0, 10, List.of("REQ-PERCENT"), 1);
            assertSearch(backend, "_", 0, 10, List.of("snake_case"), 1);
            assertSearch(backend, "e_c", 0, 10, List.of("snake_case"), 1);
            assertSearch(backend, "\\", 0, 10, List.of("REQ-PATH"), 1);
            assertSearch(backend, "c:\\data", 0, 10, List.of("REQ-PATH"), 1);
        }
    }

    @Test
    void everyTermMustBeFoundAndATermNeverSpansTwoFields() {
        for (Backend backend : backends()) {
            assertSearch(backend, "alpha-9 DELTA", 0, 10, List.of("ALPHA-9"), 1);
            assertSearch(backend, "alpha-9 epsilon", 0, 10, List.of(), 0);
            assertSearch(backend, "9beta", 0, 10, List.of(), 0);
            assertSearch(backend, "keyless", 0, 10, List.of("<no key>"), 1);
            assertSearch(backend, "coverage reach", 0, 10, List.of("REQ-PLAIN", "REQ-PERCENT"), 2);
        }
    }

    @Test
    void theSearchPagesPartitionTheMatchesInRequirementIdentityOrder() {
        List<String> all = List.of(
                "<no key>", "REQ-PLAIN", "snakeXcase", "REQ-OMEGA",
                "REQ-SCHOOL", "REQ-PERCENT", "snake_case", "REQ-PATH", "ALPHA-9");
        for (Backend backend : backends()) {
            assertSearch(backend, "", 0, 4, all.subList(0, 4), 9);
            assertSearch(backend, "", 4, 4, all.subList(4, 8), 9);
            assertSearch(backend, "", 8, 4, all.subList(8, 9), 9);
            assertSearch(backend, "", 9, 4, List.of(), 9);
            assertSearch(backend, "", 50, 4, List.of(), 9);
            assertSearch(backend, "", 0, 100, all, 9);
        }
    }

    @Test
    void theSearchOfASnapshotThatIsNotPublishedKeepsItsNamedRefusal() {
        for (Backend backend : backends()) {
            KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class,
                    () -> new RequirementQueryService(backend.snapshots, backend.requirements)
                            .findSnapshot(READY, RequirementSearchQuery.all(), PageRequest.first(10)));
            assertEquals("requirement query requires an ACTIVE or RETIRED snapshot: " + READY + " is READY",
                    refusal.getMessage(), backend.name);
        }
    }

    @Test
    void theRequirementsOfOneSpecificationArePagedWithTheChangesThatAffectAnyOfThem() {
        for (Backend backend : backends()) {
            SpecificationContextQueryService service = new SpecificationContextQueryService(
                    backend.snapshots, backend.content, backend.requirements, backend.traceability);

            SpecificationContextResult first = service.active(PROJECT, SPEC_A, new PageRequest(0, 2)).orElseThrow();
            assertEquals(List.of("<no key>", "REQ-PLAIN"), keys(first.requirements().items()), backend.name);
            assertEquals(5, first.requirements().totalMatches(), backend.name);
            assertTrue(first.requirements().hasMore(), backend.name);
            assertEquals(List.of(CHANGE_ON_A), first.changes(),
                    backend.name + ": a change that affects a requirement of the specification outside the page");

            SpecificationContextResult last = service.active(PROJECT, SPEC_A, new PageRequest(4, 2)).orElseThrow();
            assertEquals(List.of("REQ-PERCENT"), keys(last.requirements().items()), backend.name);
            assertFalse(last.requirements().hasMore(), backend.name);

            SpecificationContextResult beyond = service.active(PROJECT, SPEC_A, new PageRequest(5, 2)).orElseThrow();
            assertEquals(List.of(), beyond.requirements().items(), backend.name);
            assertEquals(5, beyond.requirements().totalMatches(), backend.name);
            assertEquals(List.of(CHANGE_ON_A), beyond.changes(), backend.name);

            SpecificationContextResult other = service.active(PROJECT, SPEC_B, new PageRequest(0, 10)).orElseThrow();
            assertEquals(List.of("snakeXcase", "snake_case", "REQ-PATH", "ALPHA-9"),
                    keys(other.requirements().items()), backend.name);
            assertEquals(List.of(CHANGE_ON_B), other.changes(), backend.name);
        }
    }

    @Test
    void theRequirementsOfOnePublishedVersionArePagedInEntityIdentityOrder() {
        List<String> all = List.of(
                "<no key>", "REQ-PLAIN", "snakeXcase", "REQ-OMEGA",
                "REQ-SCHOOL", "REQ-PERCENT", "snake_case", "REQ-PATH", "ALPHA-9");
        for (Backend backend : backends()) {
            HistoricalRequirementQueryService service =
                    new HistoricalRequirementQueryService(backend.snapshots, backend.requirements);
            List<String> pages = new ArrayList<>();
            for (int offset = 0; offset < 9; offset += 2) {
                RequirementPage page = service.requirements(ACTIVE, offset, 2);
                assertEquals(9, page.totalMatches(), backend.name);
                assertEquals(offset + 2 < 9, page.hasMoreAfter(offset), backend.name + " at offset " + offset);
                pages.addAll(keys(page.items().stream()
                        .map(record -> record.entityVersion().content()).toList()));
            }
            assertEquals(all, pages, backend.name);

            RequirementPage beyond = service.requirements(ACTIVE, 9, 2);
            assertEquals(List.of(), beyond.items(), backend.name);
            assertEquals(9, beyond.totalMatches(), backend.name);
            assertFalse(beyond.hasMoreAfter(9), backend.name);
        }
    }

    private void assertSearch(
            Backend backend, String query, int offset, int limit, List<String> expectedKeys, int expectedTotal) {
        RequirementSearchPage page = new RequirementQueryService(backend.snapshots, backend.requirements)
                .findActive(PROJECT, new RequirementSearchQuery(query), new PageRequest(offset, limit))
                .orElseThrow();
        String context = backend.name + ": query '" + query + "' offset " + offset + " limit " + limit;
        assertEquals(expectedKeys, keys(page.items().stream().map(record -> record.entityVersion().content()).toList()),
                context);
        assertEquals(expectedTotal, page.totalMatches(), context);
        assertEquals(offset + expectedKeys.size() < expectedTotal, page.hasMore(), context);
    }

    private static List<String> keys(List<Requirement> requirements) {
        return requirements.stream().map(requirement -> requirement.key().orElse("<no key>")).toList();
    }

    private List<Backend> backends() {
        MemorySpecificationKnowledgeStore memory = new MemorySpecificationKnowledgeStore();
        Backend memoryBackend = new Backend("memory", memory, memory,
                new MemorySnapshotBusinessContentStore(memory, memory), new MemoryTraceabilityStore(memory));
        memoryBackend.seed();

        Path database = tempDir.resolve("characterisation-" + UUID.randomUUID() + ".db");
        Backend sqliteBackend = new Backend("sqlite",
                open(new SqliteSpecificationKnowledgeStore(database)),
                open(new SqliteVersionedRequirementStore(database)),
                open(new SqliteSnapshotBusinessContentStore(database)),
                open(new SqliteTraceabilityStore(database)));
        sqliteBackend.seed();
        return List.of(memoryBackend, sqliteBackend);
    }

    private <T extends AutoCloseable> T open(T store) {
        opened.add(store);
        return store;
    }

    /** One identity per digit and number; a leading digit of 8 or more gives a negative most significant half. */
    static DomainIdentity identity(String leadingDigit, int number) {
        return DomainIdentity.parse(leadingDigit + "0000000-0000-7000-8000-%012d".formatted(number));
    }

    private static ChangeProposal change(int number, String key) {
        return new ChangeProposal(new ChangeId(identity("0", number)), PROJECT, Optional.of(key), key, "Deliver " + key,
                List.of("scope"), List.of(), List.of(), PROVENANCE);
    }

    private record Row(String digit, int number, String key, String title, String statement, SpecificationId spec) {
        RequirementVersionRecord record(KnowledgeSnapshotId snapshot, SpecificationVersionId version, TemporalState state) {
            RequirementId id = new RequirementId(identity(digit, number));
            Requirement requirement = new Requirement(id, spec, Optional.ofNullable(key), title, statement, PROVENANCE);
            int versionNumber = number + (snapshot.equals(ACTIVE) ? 100 : 200) + (state == TemporalState.CURRENT ? 0 : 50);
            return new RequirementVersionRecord(snapshot, new EntityVersion<>(
                    new EntityVersionId(identity("0", versionNumber)), id.value(), version, state, requirement));
        }
    }

    private record Backend(
            String name,
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            SnapshotBusinessContentStore content,
            TraceabilityStore traceability) {

        void seed() {
            snapshots.putProject(new ProjectStoreEntry(PROJECT, SourceLocator.file("workspace-page")));
            requirements.putSpecificationVersion(new SpecificationVersion(ACTIVE_VERSION, PROJECT, Optional.of(1L),
                    Optional.of("provider-v1"), Optional.of("revision-1"), T0, Optional.empty()));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(ACTIVE, PROJECT, Optional.empty(),
                    KnowledgeSnapshotState.READY, Optional.of("revision-1"), T0));
            requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(ACTIVE, ACTIVE_VERSION));
            for (Row row : ROWS) {
                requirements.putRequirementVersion(row.record(ACTIVE, ACTIVE_VERSION, TemporalState.CURRENT));
            }
            requirements.putRequirementVersion(ROWS.getFirst().record(ACTIVE, ACTIVE_VERSION, TemporalState.PROPOSED));
            content.putSnapshotContent(new SnapshotBusinessContent(ACTIVE, ACTIVE_VERSION,
                    List.of(specification(SPEC_A, "SPEC-A"), specification(SPEC_B, "SPEC-B")),
                    List.of(), List.of(CHANGE_ON_A, CHANGE_ON_B), List.of(), List.of(), List.of(), List.of(),
                    List.of(EVIDENCE)));
            traceability.putLinks(ACTIVE, List.of(
                    affects(920, CHANGE_ON_A, "f", 2),
                    affects(921, CHANGE_ON_B, "5", 9)));
            snapshots.activateSnapshot(ACTIVE, Optional.empty());

            requirements.putSpecificationVersion(new SpecificationVersion(READY_VERSION, PROJECT, Optional.of(2L),
                    Optional.of("provider-v1"), Optional.of("revision-2"), T0.plusSeconds(1),
                    Optional.of(ACTIVE_VERSION)));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(READY, PROJECT, Optional.of(ACTIVE),
                    KnowledgeSnapshotState.READY, Optional.of("revision-2"), T0.plusSeconds(1)));
            requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(READY, READY_VERSION));
            requirements.putRequirementVersion(ROWS.getFirst().record(READY, READY_VERSION, TemporalState.CURRENT));
        }

        private static Specification specification(SpecificationId id, String key) {
            return new Specification(id, PROJECT, key, key, Optional.empty(), PROVENANCE);
        }

        private static TraceabilityLink affects(int number, ChangeProposal change, String digit, int requirement) {
            return new TraceabilityLink(
                    new TraceabilityLinkId(identity("0", number)),
                    new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, change.id().value()),
                    TraceabilityRelationType.AFFECTS,
                    new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, identity(digit, requirement)),
                    TraceabilityLinkOrigin.DERIVED,
                    TraceabilityResolutionState.RESOLVED,
                    Optional.empty(),
                    Set.of(EVIDENCE.id()),
                    T0);
        }
    }
}
