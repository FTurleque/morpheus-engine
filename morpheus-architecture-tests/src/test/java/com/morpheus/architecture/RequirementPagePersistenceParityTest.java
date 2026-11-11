package com.morpheus.architecture;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementPage;
import com.morpheus.application.store.RequirementPageCriteria;
import com.morpheus.application.store.RequirementPageOrder;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
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
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The page of CURRENT requirements read through the port, on the memory store and on the SQLite store, over the same
 * requirements: every criteria, order, offset and limit gives the same items in the same order and the same total.
 * The memory store evaluates the criteria with the application's own definition and is the reference.
 *
 * <p>The fixture carries what a store can get wrong while looking right: identities whose most significant half is
 * negative, so the text order of the canonical form and the domain's order disagree; accented, non-Latin, wildcard
 * and backslash text; a requirement without a key; non-CURRENT versions and a second snapshot that must stay out.</p>
 */
class RequirementPagePersistenceParityTest {
    private static final Instant T0 = Instant.parse("2026-10-29T09:00:00Z");
    private static final ProjectSpecificationId PROJECT = new ProjectSpecificationId(identity("0", 1));
    private static final KnowledgeSnapshotId SNAPSHOT = new KnowledgeSnapshotId(identity("0", 2));
    private static final KnowledgeSnapshotId OTHER_SNAPSHOT = new KnowledgeSnapshotId(identity("0", 3));
    private static final SpecificationVersionId VERSION = new SpecificationVersionId(identity("0", 4));
    private static final SpecificationVersionId OTHER_VERSION = new SpecificationVersionId(identity("0", 5));
    private static final SpecificationId SPEC_A = new SpecificationId(identity("0", 6));
    private static final SpecificationId SPEC_B = new SpecificationId(identity("0", 7));
    private static final String[] DIGITS = {"0", "3", "7", "8", "b", "f"};
    private static final String[] TITLES = {
            "ÉCOLE publique", "Ωμέγα ΣΥΣΤΗΜΑ", "Reach 100% coverage", "snake_case naming",
            "Path C:\\data", "Plain requirement", "Straße NAME", "İstanbul office"};
    private static final List<RequirementPageCriteria> CRITERIA = List.of(
            RequirementPageCriteria.all(),
            RequirementPageCriteria.ofSpecification(SPEC_A),
            RequirementPageCriteria.ofSpecification(SPEC_B),
            RequirementPageCriteria.lexical(List.of("école")),
            RequirementPageCriteria.lexical(List.of("συστημα")),
            RequirementPageCriteria.lexical(List.of("%")),
            RequirementPageCriteria.lexical(List.of("_")),
            RequirementPageCriteria.lexical(List.of("\\")),
            RequirementPageCriteria.lexical(List.of(RequirementPageCriteria.normalize("STRASSE"))),
            RequirementPageCriteria.lexical(List.of(RequirementPageCriteria.normalize("Straße"))),
            RequirementPageCriteria.lexical(List.of(RequirementPageCriteria.normalize("İSTANBUL"))),
            RequirementPageCriteria.lexical(List.of("req", "1")),
            RequirementPageCriteria.lexical(List.of("statement", "shared")),
            RequirementPageCriteria.lexical(List.of("absent")),
            new RequirementPageCriteria(Optional.of(SPEC_B), List.of("plain")));

    @TempDir
    Path tempDir;

    @Test
    void everyPageOfEveryReadIsTheSameOnBothStores() {
        MemorySpecificationKnowledgeStore memory = new MemorySpecificationKnowledgeStore();
        seed(memory, memory);
        Path database = tempDir.resolve("requirement-page-parity.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var sqlite = new SqliteVersionedRequirementStore(database)) {
            seed(snapshots, sqlite);

            int compared = 0;
            for (RequirementPageCriteria criteria : CRITERIA) {
                for (RequirementPageOrder order : RequirementPageOrder.values()) {
                    RequirementPage whole = memory.pageCurrentRequirementVersions(SNAPSHOT, criteria, order, 0, 1_000);
                    assertEquals(whole, sqlite.pageCurrentRequirementVersions(SNAPSHOT, criteria, order, 0, 1_000),
                            describe(criteria, order, 0, 1_000));
                    assertEquals(whole.items().stream().sorted(order.comparator()).toList(), whole.items(),
                            describe(criteria, order, 0, 1_000) + ": the declared order");
                    for (int limit : new int[] {1, 3, 7}) {
                        compared += comparePages(memory, sqlite, criteria, order, limit, whole);
                    }
                }
            }
            assertTrue(compared > 500, "the parity compared " + compared + " pages");
            assertEquals(memory.countCurrentRequirementVersions(SNAPSHOT), sqlite.countCurrentRequirementVersions(SNAPSHOT));
            assertEquals(memory.countCurrentRequirementVersions(OTHER_SNAPSHOT),
                    sqlite.countCurrentRequirementVersions(OTHER_SNAPSHOT));
        }
    }

    @Test
    void theFixtureHoldsWhatTellsTheOrdersApart() {
        MemorySpecificationKnowledgeStore memory = new MemorySpecificationKnowledgeStore();
        seed(memory, memory);
        List<RequirementVersionRecord> byRequirement = memory.pageCurrentRequirementVersions(
                SNAPSHOT, RequirementPageCriteria.all(), RequirementPageOrder.REQUIREMENT_ID, 0, 1_000).items();
        List<String> texts = byRequirement.stream()
                .map(record -> record.entityVersion().content().id().toString())
                .toList();

        assertTrue(texts.getFirst().startsWith("8"), "a negative most significant half comes first: " + texts.getFirst());
        assertNotEquals(texts.stream().sorted().toList(), texts, "the text order differs from the domain order");
        assertEquals(48, memory.countCurrentRequirementVersions(SNAPSHOT));
        assertEquals(1, memory.countCurrentRequirementVersions(OTHER_SNAPSHOT));
    }

    /** Every page of one read with one limit, on both stores; returns the number of pages compared. */
    private static int comparePages(
            VersionedRequirementStore memory,
            VersionedRequirementStore sqlite,
            RequirementPageCriteria criteria,
            RequirementPageOrder order,
            int limit,
            RequirementPage whole) {
        List<RequirementVersionRecord> concatenated = new ArrayList<>();
        int compared = 0;
        for (int offset = 0; offset <= whole.totalMatches() + limit; offset += limit) {
            RequirementPage reference = memory.pageCurrentRequirementVersions(SNAPSHOT, criteria, order, offset, limit);
            assertEquals(reference, sqlite.pageCurrentRequirementVersions(SNAPSHOT, criteria, order, offset, limit),
                    describe(criteria, order, offset, limit));
            assertEquals(whole.totalMatches(), reference.totalMatches(),
                    describe(criteria, order, offset, limit) + ": the total does not depend on the page");
            concatenated.addAll(reference.items());
            compared++;
        }
        assertEquals(whole.items(), concatenated,
                describe(criteria, order, 0, limit) + ": the pages partition the matches");
        return compared;
    }

    private static String describe(RequirementPageCriteria criteria, RequirementPageOrder order, int offset, int limit) {
        return criteria + " " + order + " offset " + offset + " limit " + limit;
    }

    private static void seed(SpecificationKnowledgeStore snapshots, VersionedRequirementStore requirements) {
        snapshots.putProject(new ProjectStoreEntry(PROJECT, SourceLocator.file("workspace-parity")));
        requirements.putSpecificationVersion(new SpecificationVersion(VERSION, PROJECT, Optional.of(1L),
                Optional.of("provider-v1"), Optional.of("revision-1"), T0, Optional.empty()));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(SNAPSHOT, PROJECT, Optional.empty(),
                KnowledgeSnapshotState.READY, Optional.of("revision-1"), T0));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(SNAPSHOT, VERSION));

        int number = 100;
        for (int index = 0; index < 48; index++) {
            String digit = DIGITS[index % DIGITS.length];
            RequirementId id = new RequirementId(identity(digit, number));
            Optional<String> key = index % 5 == 0 ? Optional.empty() : Optional.of("REQ-" + index);
            String title = TITLES[index % TITLES.length];
            String statement = "Statement " + index + (index % 4 == 0 ? " shared" : "") + " plain";
            SpecificationId spec = index % 3 == 0 ? SPEC_B : SPEC_A;
            DomainIdentity versionDigit = identity(DIGITS[(index * 5) % DIGITS.length], 10_000 + number);
            requirements.putRequirementVersion(record(SNAPSHOT, VERSION, versionDigit, id.value(), id, spec, key, title,
                    statement, TemporalState.CURRENT));
            if (index % 7 == 0) {
                requirements.putRequirementVersion(record(SNAPSHOT, VERSION, identity("0", 20_000 + number), id.value(), id,
                        spec, key, title + " proposed", "statement plain", TemporalState.PROPOSED));
            }
            number++;
        }
        RequirementId historical = new RequirementId(identity(DIGITS[1], 101));
        requirements.putRequirementVersion(record(SNAPSHOT, VERSION, identity("0", 30_000), historical.value(),
                historical, SPEC_A, Optional.of("REQ-HISTORICAL"), "ÉCOLE historique", "statement shared plain",
                TemporalState.HISTORICAL));
        snapshots.activateSnapshot(SNAPSHOT, Optional.empty());

        requirements.putSpecificationVersion(new SpecificationVersion(OTHER_VERSION, PROJECT, Optional.of(2L),
                Optional.of("provider-v1"), Optional.of("revision-2"), T0.plusSeconds(1), Optional.of(VERSION)));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(OTHER_SNAPSHOT, PROJECT, Optional.of(SNAPSHOT),
                KnowledgeSnapshotState.READY, Optional.of("revision-2"), T0.plusSeconds(1)));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(OTHER_SNAPSHOT, OTHER_VERSION));
        RequirementId other = new RequirementId(identity("3", 40_000));
        requirements.putRequirementVersion(record(OTHER_SNAPSHOT, OTHER_VERSION, identity("0", 40_001), other.value(),
                other, SPEC_A, Optional.of("REQ-OTHER"), "ÉCOLE other snapshot", "statement shared plain",
                TemporalState.CURRENT));
    }

    private static RequirementVersionRecord record(
            KnowledgeSnapshotId snapshot,
            SpecificationVersionId version,
            DomainIdentity entityVersion,
            DomainIdentity entityIdentity,
            RequirementId id,
            SpecificationId spec,
            Optional<String> key,
            String title,
            String statement,
            TemporalState state) {
        Requirement requirement = new Requirement(id, spec, key, title, statement, new Provenance(
                new ProviderId("parity-fixture"), Optional.of("1"), SourceLocator.file("specs/parity.md"), key,
                Optional.of("revision-1"), new EvidenceId(identity("0", 50_000))));
        return new RequirementVersionRecord(snapshot, new EntityVersion<>(
                new EntityVersionId(entityVersion), entityIdentity, version, state, requirement));
    }

    private static DomainIdentity identity(String leadingDigit, int number) {
        return DomainIdentity.parse(leadingDigit + "0000000-0000-7000-8000-%012d".formatted(number));
    }
}
