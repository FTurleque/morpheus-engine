package com.morpheus.architecture;

import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.RequirementQueryService;
import com.morpheus.application.query.RequirementSearchPage;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.evidence.EvidenceId;
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
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementQueryContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T13:30:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteProduceExactlyTheSameLexicalResult() {
        Fixture fixture = Fixture.create();

        var memory = new MemorySpecificationKnowledgeStore();
        populate(memory, memory, fixture);
        RequirementSearchPage memoryPage = service(memory, memory)
                .findActive(fixture.projectId(), new RequirementSearchQuery("RETENTION invoice"), PageRequest.first(100))
                .orElseThrow();

        Path database = tempDir.resolve("equivalence.db");
        RequirementSearchPage sqlitePage;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            populate(snapshots, requirements, fixture);
            sqlitePage = service(snapshots, requirements)
                    .findActive(fixture.projectId(), new RequirementSearchQuery("RETENTION invoice"), PageRequest.first(100))
                    .orElseThrow();
        }

        assertEquals(memoryPage, sqlitePage);
        assertEquals(2, memoryPage.totalMatches());
        assertTrue(memoryPage.items().stream().allMatch(record ->
                record.entityVersion().temporalState() == TemporalState.CURRENT));
    }

    @Test
    void activeQueryNeverLeaksProposedRetiredOrReadyContent() {
        Fixture fixture = Fixture.create();
        var store = new MemorySpecificationKnowledgeStore();
        populate(store, store, fixture);
        RequirementQueryService service = service(store, store);

        RequirementSearchPage active = service
                .findActive(fixture.projectId(), new RequirementSearchQuery("secret-marker"), PageRequest.first(100))
                .orElseThrow();

        assertEquals(0, active.totalMatches());
        assertEquals(fixture.activeSnapshotId(), active.snapshot().id());

        RequirementSearchPage allActive = service
                .findActive(fixture.projectId(), RequirementSearchQuery.all(), PageRequest.first(100))
                .orElseThrow();
        assertEquals(fixture.activeCurrentRecords().size(), allActive.totalMatches());
        assertFalse(allActive.items().stream().anyMatch(record ->
                record.entityVersion().temporalState() == TemporalState.PROPOSED));
    }

    @Test
    void explicitHistoricalQueryAllowsRetiredAndRejectsTechnicalSnapshots() {
        Fixture fixture = Fixture.create();
        var store = new MemorySpecificationKnowledgeStore();
        populate(store, store, fixture);
        RequirementQueryService service = service(store, store);

        RequirementSearchPage retired = service.findSnapshot(
                fixture.retiredSnapshotId(), RequirementSearchQuery.all(), PageRequest.first(100));
        assertEquals(KnowledgeSnapshotState.RETIRED, retired.snapshot().state());
        assertEquals(List.of(fixture.retiredRequirementId()), ids(retired.items()));

        assertThrows(
                KnowledgeStoreException.class,
                () -> service.findSnapshot(
                        fixture.readySnapshotId(), RequirementSearchQuery.all(), PageRequest.first(100)));
        assertThrows(
                KnowledgeStoreException.class,
                () -> service.findSnapshot(
                        KnowledgeSnapshotId.generate(), RequirementSearchQuery.all(), PageRequest.first(100)));
    }

    @Test
    void lexicalSearchIsCaseInsensitiveUsesAllTermsAndDoesNotFuzzyMatch() {
        Fixture fixture = Fixture.create();
        var store = new MemorySpecificationKnowledgeStore();
        populate(store, store, fixture);
        RequirementQueryService service = service(store, store);

        RequirementSearchPage byKeyAndStatement = service
                .findActive(fixture.projectId(), new RequirementSearchQuery("req-sec SEVEN"), PageRequest.first(100))
                .orElseThrow();
        assertEquals(List.of(fixture.retentionRequirementId()), ids(byKeyAndStatement.items()));

        RequirementSearchPage crossFieldAnd = service
                .findActive(fixture.projectId(), new RequirementSearchQuery("audit retention"), PageRequest.first(100))
                .orElseThrow();
        assertEquals(List.of(fixture.auditRequirementId()), ids(crossFieldAnd.items()));

        RequirementSearchPage fuzzyTypo = service
                .findActive(fixture.projectId(), new RequirementSearchQuery("retentn"), PageRequest.first(100))
                .orElseThrow();
        assertEquals(0, fuzzyTypo.totalMatches());
    }

    @Test
    void paginationIsAppliedAfterStableRequirementIdentityOrdering() {
        Fixture fixture = Fixture.create();
        var store = new MemorySpecificationKnowledgeStore();
        populate(store, store, fixture);
        RequirementQueryService service = service(store, store);

        List<RequirementId> expected = fixture.activeCurrentRecords().stream()
                .map(record -> record.entityVersion().content().id())
                .sorted()
                .toList();

        RequirementSearchPage first = service
                .findActive(fixture.projectId(), RequirementSearchQuery.all(), new PageRequest(0, 2))
                .orElseThrow();
        RequirementSearchPage second = service
                .findActive(fixture.projectId(), RequirementSearchQuery.all(), new PageRequest(2, 2))
                .orElseThrow();
        RequirementSearchPage beyond = service
                .findActive(fixture.projectId(), RequirementSearchQuery.all(), new PageRequest(99, 2))
                .orElseThrow();

        assertEquals(expected.subList(0, 2), ids(first.items()));
        assertEquals(expected.subList(2, expected.size()), ids(second.items()));
        assertEquals(expected.size(), first.totalMatches());
        assertTrue(first.hasMore());
        assertFalse(second.hasMore());
        assertTrue(beyond.items().isEmpty());
        assertFalse(beyond.hasMore());
    }

    @Test
    void pageRequestRejectsUnboundedOrInvalidRangesAndMissingActiveIsExplicitlyEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(0, PageRequest.MAX_LIMIT + 1));

        var store = new MemorySpecificationKnowledgeStore();
        assertTrue(service(store, store)
                .findActive(ProjectSpecificationId.generate(), RequirementSearchQuery.all(), PageRequest.first(10))
                .isEmpty());
    }

    @Test
    void sqliteReopenPreservesTheSameSearchResultAndPagination() {
        Fixture fixture = Fixture.create();
        Path database = tempDir.resolve("reopen.db");
        RequirementSearchPage before;

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            populate(snapshots, requirements, fixture);
            before = service(snapshots, requirements)
                    .findActive(fixture.projectId(), new RequirementSearchQuery("invoice"), new PageRequest(0, 1))
                    .orElseThrow();
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            RequirementSearchPage after = service(snapshots, requirements)
                    .findActive(fixture.projectId(), new RequirementSearchQuery("invoice"), new PageRequest(0, 1))
                    .orElseThrow();
            assertEquals(before, after);
            assertTrue(after.totalMatches() >= 2);
            assertTrue(after.hasMore());
        }
    }

    private RequirementQueryService service(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements) {
        return new RequirementQueryService(snapshots, requirements);
    }

    private void populate(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            Fixture fixture) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace-m5")));

        requirements.putSpecificationVersion(specificationVersion(
                fixture.retiredVersionId(), fixture.projectId(), 1L, Optional.empty()));
        snapshots.putSnapshot(readySnapshot(
                fixture.retiredSnapshotId(), fixture.projectId(), Optional.empty(), "revision-1", T0));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.retiredSnapshotId(), fixture.retiredVersionId()));
        requirements.putRequirementVersion(fixture.retiredRecord());
        snapshots.activateSnapshot(fixture.retiredSnapshotId(), Optional.empty());

        requirements.putSpecificationVersion(specificationVersion(
                fixture.activeVersionId(), fixture.projectId(), 2L, Optional.of(fixture.retiredVersionId())));
        snapshots.putSnapshot(readySnapshot(
                fixture.activeSnapshotId(), fixture.projectId(), Optional.of(fixture.retiredSnapshotId()), "revision-2", T0.plusSeconds(10)));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.activeSnapshotId(), fixture.activeVersionId()));
        fixture.activeCurrentRecords().forEach(requirements::putRequirementVersion);
        requirements.putRequirementVersion(fixture.proposedRecord());
        snapshots.activateSnapshot(fixture.activeSnapshotId(), Optional.of(fixture.retiredSnapshotId()));

        requirements.putSpecificationVersion(specificationVersion(
                fixture.readyVersionId(), fixture.projectId(), 3L, Optional.of(fixture.activeVersionId())));
        snapshots.putSnapshot(readySnapshot(
                fixture.readySnapshotId(), fixture.projectId(), Optional.of(fixture.activeSnapshotId()), "revision-3", T0.plusSeconds(20)));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.readySnapshotId(), fixture.readyVersionId()));
        requirements.putRequirementVersion(fixture.readyRecord());
    }

    private SpecificationVersion specificationVersion(
            SpecificationVersionId id,
            ProjectSpecificationId projectId,
            long sequence,
            Optional<SpecificationVersionId> predecessor) {
        return new SpecificationVersion(
                id,
                projectId,
                Optional.of(sequence),
                Optional.of("provider-v1"),
                Optional.of("source-revision-" + sequence),
                T0.plusSeconds(sequence),
                predecessor);
    }

    private KnowledgeSnapshotMetadata readySnapshot(
            KnowledgeSnapshotId id,
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessor,
            String revision,
            Instant createdAt) {
        return new KnowledgeSnapshotMetadata(
                id,
                projectId,
                predecessor,
                KnowledgeSnapshotState.READY,
                Optional.of(revision),
                createdAt);
    }

    private List<RequirementId> ids(List<RequirementVersionRecord> records) {
        return records.stream()
                .map(record -> record.entityVersion().content().id())
                .toList();
    }

    private static RequirementVersionRecord record(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId specificationVersionId,
            EntityVersionId entityVersionId,
            RequirementId requirementId,
            SpecificationId specificationId,
            TemporalState state,
            Optional<String> key,
            String title,
            String statement,
            EvidenceId evidenceId) {
        Requirement requirement = new Requirement(
                requirementId,
                specificationId,
                key,
                title,
                statement,
                new Provenance(
                        new ProviderId("m5-fixture"),
                        Optional.of("1"),
                        SourceLocator.file("specs/m5.md"),
                        key,
                        Optional.of("source-revision"),
                        evidenceId));
        return new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        entityVersionId,
                        requirementId.value(),
                        specificationVersionId,
                        state,
                        requirement));
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            SpecificationId specificationId,
            KnowledgeSnapshotId retiredSnapshotId,
            KnowledgeSnapshotId activeSnapshotId,
            KnowledgeSnapshotId readySnapshotId,
            SpecificationVersionId retiredVersionId,
            SpecificationVersionId activeVersionId,
            SpecificationVersionId readyVersionId,
            RequirementVersionRecord retiredRecord,
            List<RequirementVersionRecord> activeCurrentRecords,
            RequirementVersionRecord proposedRecord,
            RequirementVersionRecord readyRecord,
            RequirementId retiredRequirementId,
            RequirementId retentionRequirementId,
            RequirementId auditRequirementId) {

        static Fixture create() {
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            SpecificationId specificationId = SpecificationId.generate();
            KnowledgeSnapshotId retiredSnapshotId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId activeSnapshotId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId readySnapshotId = KnowledgeSnapshotId.generate();
            SpecificationVersionId retiredVersionId = SpecificationVersionId.generate();
            SpecificationVersionId activeVersionId = SpecificationVersionId.generate();
            SpecificationVersionId readyVersionId = SpecificationVersionId.generate();

            RequirementId retiredId = RequirementId.generate();
            RequirementVersionRecord retired = record(
                    retiredSnapshotId, retiredVersionId, EntityVersionId.generate(), retiredId, specificationId,
                    TemporalState.CURRENT, Optional.of("REQ-OLD"), "Historical retention", "secret-marker retired invoice", EvidenceId.generate());

            RequirementId retentionId = RequirementId.generate();
            RequirementId auditId = RequirementId.generate();
            RequirementId exportId = RequirementId.generate();
            RequirementId billingId = RequirementId.generate();

            RequirementVersionRecord retention = record(
                    activeSnapshotId, activeVersionId, EntityVersionId.generate(), retentionId, specificationId,
                    TemporalState.CURRENT, Optional.of("REQ-SEC-001"), "Data Retention Policy", "Keep invoices for seven years", EvidenceId.generate());
            RequirementVersionRecord audit = record(
                    activeSnapshotId, activeVersionId, EntityVersionId.generate(), auditId, specificationId,
                    TemporalState.CURRENT, Optional.of("REQ-AUD-002"), "Audit Trail", "Record invoice retention events", EvidenceId.generate());
            RequirementVersionRecord export = record(
                    activeSnapshotId, activeVersionId, EntityVersionId.generate(), exportId, specificationId,
                    TemporalState.CURRENT, Optional.empty(), "Secure invoice export", "Allow operators to export billing records", EvidenceId.generate());
            RequirementVersionRecord billing = record(
                    activeSnapshotId, activeVersionId, EntityVersionId.generate(), billingId, specificationId,
                    TemporalState.CURRENT, Optional.of("REQ-BILL-004"), "Billing archive", "Archived invoices remain searchable", EvidenceId.generate());

            RequirementVersionRecord proposed = record(
                    activeSnapshotId, activeVersionId, EntityVersionId.generate(), retentionId, specificationId,
                    TemporalState.PROPOSED, Optional.of("REQ-SEC-001"), "Proposed retention", "secret-marker proposed invoice", EvidenceId.generate());

            RequirementId readyId = RequirementId.generate();
            RequirementVersionRecord ready = record(
                    readySnapshotId, readyVersionId, EntityVersionId.generate(), readyId, specificationId,
                    TemporalState.CURRENT, Optional.of("REQ-FUTURE"), "Future requirement", "secret-marker ready invoice", EvidenceId.generate());

            List<RequirementVersionRecord> current = List.of(retention, audit, export, billing).stream()
                    .sorted(Comparator.comparing(item -> item.entityVersion().content().id()))
                    .toList();

            return new Fixture(
                    projectId,
                    specificationId,
                    retiredSnapshotId,
                    activeSnapshotId,
                    readySnapshotId,
                    retiredVersionId,
                    activeVersionId,
                    readyVersionId,
                    retired,
                    current,
                    proposed,
                    ready,
                    retiredId,
                    retentionId,
                    auditId);
        }
    }
}
