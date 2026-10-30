package com.morpheus.store.memory;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementPage;
import com.morpheus.application.store.RequirementPageCriteria;
import com.morpheus.application.store.RequirementPageOrder;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The memory store pages and counts CURRENT requirements itself, with the application's own criteria. */
class MemoryRequirementPageTest {
    private static final Instant T0 = Instant.parse("2026-10-29T11:00:00Z");
    private static final ProjectSpecificationId PROJECT = new ProjectSpecificationId(identity("0", 1));
    private static final KnowledgeSnapshotId SNAPSHOT = new KnowledgeSnapshotId(identity("0", 2));
    private static final SpecificationVersionId VERSION = new SpecificationVersionId(identity("0", 3));
    private static final SpecificationId SPEC = new SpecificationId(identity("0", 4));

    @Test
    void theStorePagesTheCurrentVersionsThatMatchInTheDeclaredOrder() {
        MemorySpecificationKnowledgeStore store = new MemorySpecificationKnowledgeStore();
        store.putProject(new ProjectStoreEntry(PROJECT, SourceLocator.file("workspace-page")));
        store.putSpecificationVersion(new SpecificationVersion(VERSION, PROJECT, Optional.of(1L),
                Optional.of("provider-v1"), Optional.of("revision-1"), T0, Optional.empty()));
        store.putSnapshot(new KnowledgeSnapshotMetadata(SNAPSHOT, PROJECT, Optional.empty(),
                KnowledgeSnapshotState.READY, Optional.of("revision-1"), T0));
        store.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(SNAPSHOT, VERSION));
        RequirementVersionRecord school = record("1", 10, "ÉCOLE", TemporalState.CURRENT);
        RequirementVersionRecord negative = record("f", 11, "École négative", TemporalState.CURRENT);
        RequirementVersionRecord other = record("2", 12, "Other", TemporalState.CURRENT);
        List.of(school, negative, other, record("3", 13, "École proposée", TemporalState.PROPOSED))
                .forEach(store::putRequirementVersion);

        assertEquals(new RequirementPage(List.of(negative), 2), store.pageCurrentRequirementVersions(
                SNAPSHOT, RequirementPageCriteria.lexical(List.of("école")), RequirementPageOrder.REQUIREMENT_ID, 0, 1));
        assertEquals(new RequirementPage(List.of(school), 2), store.pageCurrentRequirementVersions(
                SNAPSHOT, RequirementPageCriteria.lexical(List.of("école")), RequirementPageOrder.ENTITY_IDENTITY, 1, 1));
        assertEquals(new RequirementPage(List.of(), 3), store.pageCurrentRequirementVersions(
                SNAPSHOT, RequirementPageCriteria.ofSpecification(SPEC), RequirementPageOrder.REQUIREMENT_ID, 5, 1));
        assertEquals(3, store.countCurrentRequirementVersions(SNAPSHOT));

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> store.pageCurrentRequirementVersions(
                        SNAPSHOT, RequirementPageCriteria.all(), RequirementPageOrder.REQUIREMENT_ID, 0, 0));
        assertEquals("requirement page limit must be positive", refusal.getMessage());
    }

    private static RequirementVersionRecord record(String digit, int number, String title, TemporalState state) {
        RequirementId id = new RequirementId(identity(digit, number));
        Requirement requirement = new Requirement(id, SPEC, Optional.empty(), title, "statement", new Provenance(
                new ProviderId("memory-page"), Optional.empty(), SourceLocator.file("specs/page.md"), Optional.empty(),
                Optional.empty(), new EvidenceId(identity("0", 99))));
        return new RequirementVersionRecord(SNAPSHOT, new EntityVersion<>(
                new EntityVersionId(identity("0", 100 + number)), id.value(), VERSION, state, requirement));
    }

    private static DomainIdentity identity(String leadingDigit, int number) {
        return DomainIdentity.parse(leadingDigit + "0000000-0000-7000-8000-%012d".formatted(number));
    }
}
