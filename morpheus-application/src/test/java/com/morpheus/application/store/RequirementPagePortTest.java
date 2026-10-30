package com.morpheus.application.store;

import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The page of CURRENT requirements as the port defines it: the criteria, the total order, the page and its total, and
 * the default a store that does not evaluate them itself inherits - read on a store double that only lists versions.
 */
class RequirementPagePortTest {
    private static final KnowledgeSnapshotId SNAPSHOT = new KnowledgeSnapshotId(identity("0", 1));
    private static final KnowledgeSnapshotId OTHER_SNAPSHOT = new KnowledgeSnapshotId(identity("0", 2));
    private static final SpecificationVersionId VERSION = new SpecificationVersionId(identity("0", 3));
    private static final SpecificationId SPEC_A = new SpecificationId(identity("0", 4));
    private static final SpecificationId SPEC_B = new SpecificationId(identity("0", 5));

    @Test
    void aTermIsANormalizedWordWithoutWhitespace() {
        IllegalArgumentException upperCase = assertThrows(IllegalArgumentException.class,
                () -> RequirementPageCriteria.lexical(List.of("École")));
        assertEquals("a requirement page term must be a normalized word without whitespace: 'École'",
                upperCase.getMessage());
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> RequirementPageCriteria.lexical(List.of("")));
        assertEquals("a requirement page term must be a normalized word without whitespace: ''", blank.getMessage());
        IllegalArgumentException twoWords = assertThrows(IllegalArgumentException.class,
                () -> RequirementPageCriteria.lexical(List.of("two\twords")));
        assertTrue(twoWords.getMessage().endsWith("'two\twords'"), twoWords.getMessage());
        NullPointerException noSpecification = assertThrows(NullPointerException.class,
                () -> new RequirementPageCriteria(null, List.of()));
        assertEquals("specificationId", noSpecification.getMessage());
    }

    @Test
    void theCriteriaMatchEveryTermInTheNormalizedCorpusAndTheSpecification() {
        Requirement school = requirement("1", 1, SPEC_A, "REQ-ÉCOLE", "École PUBLIQUE", "100% des élèves");
        Requirement keyless = requirement("2", 2, SPEC_B, null, "Keyless", "under C:\\data");

        assertTrue(RequirementPageCriteria.all().matches(school));
        assertTrue(RequirementPageCriteria.lexical(List.of("publique", "élèves")).matches(school));
        assertTrue(RequirementPageCriteria.lexical(List.of("%")).matches(school));
        assertFalse(RequirementPageCriteria.lexical(List.of("ecole")).matches(school));
        assertFalse(RequirementPageCriteria.lexical(List.of("publique100%")).matches(school),
                "a term never spans two fields");
        assertTrue(RequirementPageCriteria.lexical(List.of("c:\\data")).matches(keyless));
        assertTrue(RequirementPageCriteria.ofSpecification(SPEC_A).matches(school));
        assertFalse(RequirementPageCriteria.ofSpecification(SPEC_A).matches(keyless));
        assertFalse(new RequirementPageCriteria(Optional.of(SPEC_B), List.of("publique")).matches(school));
        assertEquals("keyless under c:\\data",
                RequirementPageCriteria.corpus(Optional.empty(), "Keyless", "under C:\\data"));
    }

    @Test
    void aPageCountsAtLeastItsItemsAndSaysWhetherMoreFollow() {
        RequirementVersionRecord one = current(SNAPSHOT, "1", 1, 1, SPEC_A, "One");
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> new RequirementPage(List.of(one), 0));
        assertEquals("totalMatches must count at least the items of the page: 0 < 1", refusal.getMessage());

        RequirementPage page = new RequirementPage(List.of(one), 3);
        assertTrue(page.hasMoreAfter(0));
        assertTrue(page.hasMoreAfter(1));
        assertFalse(page.hasMoreAfter(2));
        assertFalse(new RequirementPage(List.of(), 3).hasMoreAfter(3));
    }

    @Test
    void eachOrderComparesIdentitiesOnTheirSignedHalvesLikeTheDomain() {
        RequirementVersionRecord negative = current(SNAPSHOT, "8", 9, 20, SPEC_A, "Negative");
        RequirementVersionRecord positive = current(SNAPSHOT, "1", 1, 30, SPEC_A, "Positive");
        RequirementVersionRecord larger = current(SNAPSHOT, "7", 2, 10, SPEC_A, "Larger");

        for (RequirementPageOrder order : RequirementPageOrder.values()) {
            assertEquals(List.of(negative, positive, larger),
                    List.of(larger, positive, negative).stream().sorted(order.comparator()).toList(), order.name());
        }
    }

    @Test
    void theDefaultPageKeepsTheCurrentVersionsOfTheSnapshotThatMatchInTheDeclaredOrder() {
        ListingStore store = new ListingStore();
        RequirementVersionRecord first = current(SNAPSHOT, "9", 1, 1, SPEC_A, "Alpha target");
        RequirementVersionRecord second = current(SNAPSHOT, "1", 2, 2, SPEC_A, "Beta target");
        RequirementVersionRecord third = current(SNAPSHOT, "2", 3, 3, SPEC_B, "Gamma target");
        store.versions.addAll(List.of(third, second, first,
                record(SNAPSHOT, "3", 4, identity("0", 4), identity("3", 4), SPEC_A, "Proposed target",
                        TemporalState.PROPOSED),
                current(OTHER_SNAPSHOT, "4", 5, 5, SPEC_A, "Other target")));

        RequirementPageCriteria target = RequirementPageCriteria.lexical(List.of("target"));
        assertEquals(new RequirementPage(List.of(first, second), 3),
                store.pageCurrentRequirementVersions(SNAPSHOT, target, RequirementPageOrder.REQUIREMENT_ID, 0, 2));
        assertEquals(new RequirementPage(List.of(third), 3),
                store.pageCurrentRequirementVersions(SNAPSHOT, target, RequirementPageOrder.REQUIREMENT_ID, 2, 2));
        assertEquals(new RequirementPage(List.of(), 3),
                store.pageCurrentRequirementVersions(SNAPSHOT, target, RequirementPageOrder.REQUIREMENT_ID, 3, 2));
        assertEquals(new RequirementPage(List.of(), 3),
                store.pageCurrentRequirementVersions(
                        SNAPSHOT, target, RequirementPageOrder.REQUIREMENT_ID, Integer.MAX_VALUE, 2));
        assertEquals(new RequirementPage(List.of(third), 1),
                store.pageCurrentRequirementVersions(SNAPSHOT, RequirementPageCriteria.ofSpecification(SPEC_B),
                        RequirementPageOrder.ENTITY_IDENTITY, 0, Integer.MAX_VALUE));
        assertEquals(3, store.countCurrentRequirementVersions(SNAPSHOT));
        assertEquals(1, store.countCurrentRequirementVersions(OTHER_SNAPSHOT));
    }

    @Test
    void thePageBoundsAreRefusedByName() {
        ListingStore store = new ListingStore();
        IllegalArgumentException negativeOffset = assertThrows(IllegalArgumentException.class,
                () -> store.pageCurrentRequirementVersions(
                        SNAPSHOT, RequirementPageCriteria.all(), RequirementPageOrder.REQUIREMENT_ID, -1, 1));
        assertEquals("requirement page offset must be greater than or equal to zero", negativeOffset.getMessage());
        IllegalArgumentException emptyPage = assertThrows(IllegalArgumentException.class,
                () -> store.pageCurrentRequirementVersions(
                        SNAPSHOT, RequirementPageCriteria.all(), RequirementPageOrder.REQUIREMENT_ID, 0, 0));
        assertEquals("requirement page limit must be positive", emptyPage.getMessage());
        NullPointerException noOrder = assertThrows(NullPointerException.class,
                () -> store.pageCurrentRequirementVersions(SNAPSHOT, RequirementPageCriteria.all(), null, 0, 1));
        assertEquals("order", noOrder.getMessage());
    }

    private static Requirement requirement(
            String digit, int number, SpecificationId spec, String key, String title, String statement) {
        return new Requirement(new RequirementId(identity(digit, number)), spec, Optional.ofNullable(key), title,
                statement, new Provenance(new ProviderId("port-fixture"), Optional.empty(),
                        SourceLocator.file("specs/port.md"), Optional.empty(), Optional.empty(),
                        new EvidenceId(identity("0", 99))));
    }

    private static RequirementVersionRecord current(
            KnowledgeSnapshotId snapshot, String digit, int number, int version, SpecificationId spec, String title) {
        return record(snapshot, digit, number, identity("0", 100 + version), identity(digit, number), spec, title,
                TemporalState.CURRENT);
    }

    private static RequirementVersionRecord record(
            KnowledgeSnapshotId snapshot,
            String digit,
            int number,
            DomainIdentity entityVersion,
            DomainIdentity entityIdentity,
            SpecificationId spec,
            String title,
            TemporalState state) {
        return new RequirementVersionRecord(snapshot, new EntityVersion<>(new EntityVersionId(entityVersion),
                entityIdentity, VERSION, state, requirement(digit, number, spec, null, title, "statement")));
    }

    private static DomainIdentity identity(String leadingDigit, int number) {
        return DomainIdentity.parse(leadingDigit + "0000000-0000-7000-8000-%012d".formatted(number));
    }

    /** A store that only lists what it holds, so every page and count comes from the port's defaults. */
    private static final class ListingStore implements VersionedRequirementStore {
        private final List<RequirementVersionRecord> versions = new ArrayList<>();

        @Override
        public void putSpecificationVersion(SpecificationVersion version) {
            throw new UnsupportedOperationException("not read by the page");
        }

        @Override
        public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) {
            throw new UnsupportedOperationException("not read by the page");
        }

        @Override
        public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) {
            throw new UnsupportedOperationException("not read by the page");
        }

        @Override
        public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) {
            throw new UnsupportedOperationException("not read by the page");
        }

        @Override
        public void putRequirementVersion(RequirementVersionRecord record) {
            versions.add(record);
        }

        @Override
        public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) {
            throw new UnsupportedOperationException("not read by the page");
        }

        @Override
        public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) {
            return versions.stream().filter(record -> record.snapshotId().equals(snapshotId)).toList();
        }

        @Override
        public Optional<RequirementVersionRecord> currentRequirement(
                KnowledgeSnapshotId snapshotId, DomainIdentity entityIdentity) {
            throw new UnsupportedOperationException("not read by the page");
        }
    }
}
