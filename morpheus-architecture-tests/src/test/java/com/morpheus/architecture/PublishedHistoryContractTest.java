package com.morpheus.architecture;

import com.morpheus.application.delta.RequirementDeltaApplicationPlan;
import com.morpheus.application.delta.RequirementDeltaApplicationResult;
import com.morpheus.application.delta.RequirementDeltaApplicationService;
import com.morpheus.application.delta.RequirementDeltaPromotionService;
import com.morpheus.application.delta.RequirementPromotionEvidence;
import com.morpheus.application.history.HistoricalRequirementQueryService;
import com.morpheus.application.history.PublishedHistoryException;
import com.morpheus.application.history.PublishedSnapshotHistoryService;
import com.morpheus.application.history.RequirementLogicalRollbackPlan;
import com.morpheus.application.history.RequirementLogicalRollbackRequest;
import com.morpheus.application.history.RequirementLogicalRollbackService;
import com.morpheus.application.history.RequirementSnapshotChangeKind;
import com.morpheus.application.history.RequirementSnapshotComparison;
import com.morpheus.application.history.RequirementSnapshotComparisonService;
import com.morpheus.application.history.RequirementSnapshotDifference;
import com.morpheus.application.snapshot.SnapshotLifecycleService;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishedHistoryContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryPublishesQueriesComparesAndRollsBackWithoutReactivatingHistory() {
        var store = new MemorySpecificationKnowledgeStore();
        Fixture fixture = fixture();
        seedTwoPublishedSnapshots(store, store, fixture);

        verifyPublishedHistoryBeforeRollback(store, store, fixture);
        RollbackResult rollback = executeRollback(store, store, fixture);
        verifyRollbackResult(store, store, fixture, rollback);
    }

    @Test
    void sqliteKeepsPublishedHistoryQueryableAndComparableAfterReopen() {
        Path database = tempDir.resolve("published-history.db");
        Fixture fixture = fixture();
        RollbackResult rollback;

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            seedTwoPublishedSnapshots(snapshots, requirements, fixture);
            verifyPublishedHistoryBeforeRollback(snapshots, requirements, fixture);
            rollback = executeRollback(snapshots, requirements, fixture);
            verifyRollbackResult(snapshots, requirements, fixture, rollback);
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            assertEquals(rollback.snapshotId(), snapshots.activeSnapshot(fixture.projectId()).orElseThrow().id());
            assertEquals(KnowledgeSnapshotState.RETIRED,
                    snapshots.findSnapshot(fixture.snapshot1Id()).orElseThrow().state());
            assertEquals(KnowledgeSnapshotState.RETIRED,
                    snapshots.findSnapshot(fixture.snapshot2Id()).orElseThrow().state());

            List<KnowledgeSnapshotMetadata> lineage = new PublishedSnapshotHistoryService(snapshots)
                    .lineage(fixture.projectId());
            assertEquals(List.of(fixture.snapshot1Id(), fixture.snapshot2Id(), rollback.snapshotId()),
                    lineage.stream().map(KnowledgeSnapshotMetadata::id).toList());

            HistoricalRequirementQueryService historical = new HistoricalRequirementQueryService(snapshots, requirements);
            assertEquals("retain sessions for 30 minutes",
                    historical.requirement(fixture.snapshot1Id(), fixture.modifiedId().value())
                            .orElseThrow().entityVersion().content().statement());

            RequirementSnapshotComparison comparison = new RequirementSnapshotComparisonService(snapshots, requirements)
                    .compare(fixture.snapshot1Id(), rollback.snapshotId());
            assertTrue(comparison.differences().stream()
                    .allMatch(difference -> difference.kind() == RequirementSnapshotChangeKind.UNCHANGED));
        }
    }

    @Test
    void historicalQueriesAndComparisonsRejectNonPublishedCandidates() {
        var store = new MemorySpecificationKnowledgeStore();
        Fixture fixture = fixture();
        seedTwoPublishedSnapshots(store, store, fixture);
        KnowledgeSnapshotId candidateId = KnowledgeSnapshotId.generate();
        store.putSnapshot(new KnowledgeSnapshotMetadata(
                candidateId,
                fixture.projectId(),
                Optional.of(fixture.snapshot2Id()),
                KnowledgeSnapshotState.BUILDING,
                Optional.of("candidate-revision"),
                T0.plusSeconds(30)));

        HistoricalRequirementQueryService historical = new HistoricalRequirementQueryService(store, store);
        RequirementSnapshotComparisonService comparison = new RequirementSnapshotComparisonService(store, store);

        assertThrows(PublishedHistoryException.class, () -> historical.requirements(candidateId));
        assertThrows(PublishedHistoryException.class,
                () -> comparison.compare(fixture.snapshot2Id(), candidateId));
        assertEquals(2, new PublishedSnapshotHistoryService(store).lineage(fixture.projectId()).size());
    }

    @Test
    void logicalRollbackRequiresExactlyOneExplicitDeltaIdPerChangedIdentity() {
        var store = new MemorySpecificationKnowledgeStore();
        Fixture fixture = fixture();
        seedTwoPublishedSnapshots(store, store, fixture);

        RequirementLogicalRollbackRequest incomplete = new RequirementLogicalRollbackRequest(
                fixture.projectId(),
                fixture.snapshot1Id(),
                ChangeId.generate(),
                Map.of(fixture.modifiedId().value(), RequirementDeltaId.generate()),
                Map.of(fixture.specificationId(), "auth-session"));

        assertThrows(PublishedHistoryException.class,
                () -> new RequirementLogicalRollbackService(store, store).plan(incomplete));
    }

    @Test
    void logicalRollbackRejectsCrossSpecificationMoveUntilMovedPolicyExists() {
        var store = new MemorySpecificationKnowledgeStore();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SpecificationId specification1 = SpecificationId.generate();
        SpecificationId specification2 = SpecificationId.generate();
        RequirementId requirementId = RequirementId.generate();
        KnowledgeSnapshotId snapshot1 = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId snapshot2 = KnowledgeSnapshotId.generate();
        SpecificationVersion version1 = version(projectId, 1L, Optional.empty(), T0);
        SpecificationVersion version2 = version(projectId, 2L, Optional.of(version1.id()), T0.plusSeconds(10));

        store.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-cross-spec")));
        putPublishedCandidate(store, store, snapshot1, projectId, Optional.empty(), version1,
                List.of(requirement(requirementId, specification1, "REQ", "Requirement", "version one", "v1")));
        store.activateSnapshot(snapshot1, Optional.empty());
        putPublishedCandidate(store, store, snapshot2, projectId, Optional.of(snapshot1), version2,
                List.of(requirement(requirementId, specification2, "REQ", "Requirement", "version two", "v2")));
        store.activateSnapshot(snapshot2, Optional.of(snapshot1));

        RequirementLogicalRollbackRequest request = new RequirementLogicalRollbackRequest(
                projectId,
                snapshot1,
                ChangeId.generate(),
                Map.of(requirementId.value(), RequirementDeltaId.generate()),
                Map.of(specification1, "spec-one", specification2, "spec-two"));

        PublishedHistoryException failure = assertThrows(
                PublishedHistoryException.class,
                () -> new RequirementLogicalRollbackService(store, store).plan(request));
        assertTrue(failure.getMessage().contains("MOVED"));
    }

    private void verifyPublishedHistoryBeforeRollback(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            Fixture fixture) {
        List<KnowledgeSnapshotMetadata> lineage = new PublishedSnapshotHistoryService(snapshots)
                .lineage(fixture.projectId());
        assertEquals(List.of(fixture.snapshot1Id(), fixture.snapshot2Id()),
                lineage.stream().map(KnowledgeSnapshotMetadata::id).toList());
        assertEquals(KnowledgeSnapshotState.RETIRED, lineage.get(0).state());
        assertEquals(KnowledgeSnapshotState.ACTIVE, lineage.get(1).state());

        HistoricalRequirementQueryService historical = new HistoricalRequirementQueryService(snapshots, requirements);
        assertEquals(3, historical.requirements(fixture.snapshot1Id()).size());
        assertEquals(3, historical.requirements(fixture.snapshot2Id()).size(),
                "PROPOSED occurrences must not leak into the published CURRENT projection");
        assertEquals("retain sessions for 30 minutes",
                historical.requirement(fixture.snapshot1Id(), fixture.modifiedId().value())
                        .orElseThrow().entityVersion().content().statement());
        assertTrue(historical.requirement(fixture.snapshot1Id(), fixture.addedId().value()).isEmpty());

        RequirementSnapshotComparison comparison = new RequirementSnapshotComparisonService(snapshots, requirements)
                .compare(fixture.snapshot1Id(), fixture.snapshot2Id());
        Map<DomainIdentity, RequirementSnapshotChangeKind> kinds = kindsByIdentity(comparison);
        assertEquals(RequirementSnapshotChangeKind.UNCHANGED, kinds.get(fixture.unchangedId().value()));
        assertEquals(RequirementSnapshotChangeKind.MODIFIED, kinds.get(fixture.modifiedId().value()));
        assertEquals(RequirementSnapshotChangeKind.REMOVED, kinds.get(fixture.removedId().value()));
        assertEquals(RequirementSnapshotChangeKind.ADDED, kinds.get(fixture.addedId().value()));

        RequirementSnapshotDifference unchanged = comparison.differences().stream()
                .filter(difference -> difference.entityIdentity().equals(fixture.unchangedId().value()))
                .findFirst()
                .orElseThrow();
        assertNotEquals(
                unchanged.source().orElseThrow().entityVersion().id(),
                unchanged.target().orElseThrow().entityVersion().id(),
                "different EntityVersionId values alone must not mean MODIFIED");
        assertEquals(
                unchanged.source().orElseThrow().entityVersion().content(),
                unchanged.target().orElseThrow().entityVersion().content());
    }

    private RollbackResult executeRollback(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            Fixture fixture) {
        ChangeId rollbackChangeId = ChangeId.generate();
        Map<DomainIdentity, RequirementDeltaId> deltaIds = Map.of(
                fixture.modifiedId().value(), RequirementDeltaId.generate(),
                fixture.removedId().value(), RequirementDeltaId.generate(),
                fixture.addedId().value(), RequirementDeltaId.generate());
        RequirementLogicalRollbackPlan rollbackPlan = new RequirementLogicalRollbackService(snapshots, requirements)
                .plan(new RequirementLogicalRollbackRequest(
                        fixture.projectId(),
                        fixture.snapshot1Id(),
                        rollbackChangeId,
                        deltaIds,
                        Map.of(fixture.specificationId(), "auth-session")));

        assertFalse(rollbackPlan.isNoOp());
        assertEquals(fixture.snapshot2Id(), rollbackPlan.currentSnapshot().id());
        assertEquals(fixture.snapshot1Id(), rollbackPlan.targetHistoricalSnapshot().id());
        assertEquals(rollbackChangeId, rollbackPlan.changeId());
        Map<DomainIdentity, RequirementDeltaKind> deltaKinds = deltaKindsByIdentity(rollbackPlan.deltas());
        assertEquals(RequirementDeltaKind.MODIFIED, deltaKinds.get(fixture.modifiedId().value()));
        assertEquals(RequirementDeltaKind.ADDED, deltaKinds.get(fixture.removedId().value()));
        assertEquals(RequirementDeltaKind.REMOVED, deltaKinds.get(fixture.addedId().value()));
        assertFalse(deltaKinds.containsKey(fixture.unchangedId().value()));

        RequirementDelta modifiedBack = rollbackPlan.deltas().stream()
                .filter(delta -> delta.requirementId().equals(fixture.modifiedId()))
                .findFirst()
                .orElseThrow();
        assertEquals("retain sessions for 30 minutes", modifiedBack.statement().orElseThrow());
        assertEquals(fixture.version1Modified().provenance(), modifiedBack.provenance());

        SpecificationVersion rollbackVersion = version(
                fixture.projectId(),
                3L,
                Optional.of(fixture.version2().id()),
                T0.plusSeconds(20));
        KnowledgeSnapshotId rollbackSnapshotId = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotMetadata rollbackSnapshot = new KnowledgeSnapshotMetadata(
                rollbackSnapshotId,
                fixture.projectId(),
                Optional.of(fixture.snapshot2Id()),
                KnowledgeSnapshotState.BUILDING,
                Optional.of("rollback-revision"),
                T0.plusSeconds(20));
        Map<DomainIdentity, EntityVersionId> newOccurrences = Map.of(
                fixture.unchangedId().value(), EntityVersionId.generate(),
                fixture.modifiedId().value(), EntityVersionId.generate(),
                fixture.removedId().value(), EntityVersionId.generate());

        RequirementDeltaApplicationResult applied = new RequirementDeltaApplicationService(snapshots, requirements)
                .apply(new RequirementDeltaApplicationPlan(
                        fixture.projectId(),
                        rollbackVersion,
                        rollbackSnapshot,
                        rollbackPlan.deltas(),
                        rollbackPlan.specificationIdsByKey(),
                        newOccurrences,
                        EvidenceId.generate()));
        assertEquals(KnowledgeSnapshotState.BUILDING,
                snapshots.findSnapshot(rollbackSnapshotId).orElseThrow().state());
        assertEquals(fixture.snapshot2Id(), snapshots.activeSnapshot(fixture.projectId()).orElseThrow().id(),
                "rollback planning/application must not reactivate historical state");

        new RequirementDeltaPromotionService(snapshots, requirements).promote(
                applied,
                new RequirementPromotionEvidence(
                        EvidenceId.generate(),
                        "Explicit logical rollback candidate reviewed",
                        T0.plusSeconds(25)));
        assertEquals(KnowledgeSnapshotState.READY,
                snapshots.findSnapshot(rollbackSnapshotId).orElseThrow().state());
        assertEquals(fixture.snapshot2Id(), snapshots.activeSnapshot(fixture.projectId()).orElseThrow().id());

        new SnapshotLifecycleService(snapshots).activate(rollbackSnapshotId);
        return new RollbackResult(rollbackSnapshotId, rollbackVersion.id());
    }

    private void verifyRollbackResult(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            Fixture fixture,
            RollbackResult rollback) {
        assertEquals(rollback.snapshotId(), snapshots.activeSnapshot(fixture.projectId()).orElseThrow().id());
        assertEquals(KnowledgeSnapshotState.RETIRED,
                snapshots.findSnapshot(fixture.snapshot1Id()).orElseThrow().state());
        assertEquals(KnowledgeSnapshotState.RETIRED,
                snapshots.findSnapshot(fixture.snapshot2Id()).orElseThrow().state());

        RequirementSnapshotComparison reconstructed = new RequirementSnapshotComparisonService(snapshots, requirements)
                .compare(fixture.snapshot1Id(), rollback.snapshotId());
        assertEquals(3, reconstructed.differences().size());
        assertTrue(reconstructed.differences().stream()
                .allMatch(difference -> difference.kind() == RequirementSnapshotChangeKind.UNCHANGED));

        HistoricalRequirementQueryService historical = new HistoricalRequirementQueryService(snapshots, requirements);
        RequirementVersionRecord originalModified = historical
                .requirement(fixture.snapshot1Id(), fixture.modifiedId().value()).orElseThrow();
        RequirementVersionRecord reconstructedModified = historical
                .requirement(rollback.snapshotId(), fixture.modifiedId().value()).orElseThrow();
        assertEquals(originalModified.entityVersion().content(), reconstructedModified.entityVersion().content());
        assertNotEquals(originalModified.entityVersion().id(), reconstructedModified.entityVersion().id(),
                "logical rollback must reconstruct new occurrences rather than reuse historical EntityVersionId");
        assertEquals("retain sessions for 30 minutes", originalModified.entityVersion().content().statement(),
                "historical snapshot content must remain unchanged after rollback");
        assertTrue(historical.requirement(rollback.snapshotId(), fixture.addedId().value()).isEmpty());
        assertTrue(historical.requirement(rollback.snapshotId(), fixture.removedId().value()).isPresent());
    }

    private void seedTwoPublishedSnapshots(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            Fixture fixture) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace-history")));
        putPublishedCandidate(
                snapshots,
                requirements,
                fixture.snapshot1Id(),
                fixture.projectId(),
                Optional.empty(),
                fixture.version1(),
                List.of(fixture.version1Unchanged(), fixture.version1Modified(), fixture.version1Removed()));
        snapshots.activateSnapshot(fixture.snapshot1Id(), Optional.empty());

        putPublishedCandidate(
                snapshots,
                requirements,
                fixture.snapshot2Id(),
                fixture.projectId(),
                Optional.of(fixture.snapshot1Id()),
                fixture.version2(),
                List.of(fixture.version1Unchanged(), fixture.version2Modified(), fixture.version2Added()));
        requirements.putRequirementVersion(new RequirementVersionRecord(
                fixture.snapshot2Id(),
                new EntityVersion<>(
                        EntityVersionId.generate(),
                        fixture.modifiedId().value(),
                        fixture.version2().id(),
                        TemporalState.PROPOSED,
                        requirement(
                                fixture.modifiedId(),
                                fixture.specificationId(),
                                "SESSION_EXPIRATION",
                                "Session expiration",
                                "retain sessions for 15 minutes",
                                "proposal"))));
        snapshots.activateSnapshot(fixture.snapshot2Id(), Optional.of(fixture.snapshot1Id()));
    }

    private void putPublishedCandidate(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            KnowledgeSnapshotId snapshotId,
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessorId,
            SpecificationVersion version,
            List<Requirement> currentRequirements) {
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                predecessorId,
                KnowledgeSnapshotState.READY,
                Optional.of("revision-" + version.sequence().orElseThrow()),
                version.createdAt()));
        requirements.putSpecificationVersion(version);
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, version.id()));
        currentRequirements.forEach(requirement -> requirements.putRequirementVersion(
                currentRecord(snapshotId, version.id(), requirement)));
    }

    private Fixture fixture() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        RequirementId unchangedId = RequirementId.generate();
        RequirementId modifiedId = RequirementId.generate();
        RequirementId removedId = RequirementId.generate();
        RequirementId addedId = RequirementId.generate();
        KnowledgeSnapshotId snapshot1Id = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId snapshot2Id = KnowledgeSnapshotId.generate();
        SpecificationVersion version1 = version(projectId, 1L, Optional.empty(), T0);
        SpecificationVersion version2 = version(projectId, 2L, Optional.of(version1.id()), T0.plusSeconds(10));

        Requirement version1Unchanged = requirement(
                unchangedId,
                specificationId,
                "PASSWORD_RULE",
                "Password rule",
                "passwords require twelve characters",
                "unchanged");
        Requirement version1Modified = requirement(
                modifiedId,
                specificationId,
                "SESSION_EXPIRATION",
                "Session expiration",
                "retain sessions for 30 minutes",
                "modified-v1");
        Requirement version1Removed = requirement(
                removedId,
                specificationId,
                "LEGACY_TOKEN",
                "Legacy token",
                "legacy tokens are accepted",
                "removed-v1");
        Requirement version2Modified = requirement(
                modifiedId,
                specificationId,
                "SESSION_EXPIRATION",
                "Session expiration",
                "retain sessions for 60 minutes",
                "modified-v2");
        Requirement version2Added = requirement(
                addedId,
                specificationId,
                "REMEMBER_ME",
                "Remember me",
                "remember-me requires explicit opt-in",
                "added-v2");

        return new Fixture(
                projectId,
                specificationId,
                unchangedId,
                modifiedId,
                removedId,
                addedId,
                snapshot1Id,
                snapshot2Id,
                version1,
                version2,
                version1Unchanged,
                version1Modified,
                version1Removed,
                version2Modified,
                version2Added);
    }

    private static SpecificationVersion version(
            ProjectSpecificationId projectId,
            long sequence,
            Optional<SpecificationVersionId> predecessor,
            Instant createdAt) {
        return new SpecificationVersion(
                SpecificationVersionId.generate(),
                projectId,
                Optional.of(sequence),
                Optional.of("test-provider-v" + sequence),
                Optional.of("source-revision-" + sequence),
                createdAt,
                predecessor);
    }

    private static RequirementVersionRecord currentRecord(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            Requirement requirement) {
        return new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        EntityVersionId.generate(),
                        requirement.id().value(),
                        versionId,
                        TemporalState.CURRENT,
                        requirement));
    }

    private static Requirement requirement(
            RequirementId id,
            SpecificationId specificationId,
            String key,
            String title,
            String statement,
            String evidenceKey) {
        return new Requirement(
                id,
                specificationId,
                Optional.of(key),
                title,
                statement,
                new Provenance(
                        new ProviderId("test-provider"),
                        Optional.of("1"),
                        SourceLocator.file("specs/" + evidenceKey + ".md"),
                        Optional.of(evidenceKey),
                        Optional.of("source-revision"),
                        EvidenceId.generate()));
    }

    private static Map<DomainIdentity, RequirementSnapshotChangeKind> kindsByIdentity(
            RequirementSnapshotComparison comparison) {
        Map<DomainIdentity, RequirementSnapshotChangeKind> indexed = new HashMap<>();
        comparison.differences().forEach(difference -> indexed.put(difference.entityIdentity(), difference.kind()));
        return indexed;
    }

    private static Map<DomainIdentity, RequirementDeltaKind> deltaKindsByIdentity(List<RequirementDelta> deltas) {
        Map<DomainIdentity, RequirementDeltaKind> indexed = new HashMap<>();
        deltas.forEach(delta -> indexed.put(delta.requirementId().value(), delta.kind()));
        return indexed;
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            SpecificationId specificationId,
            RequirementId unchangedId,
            RequirementId modifiedId,
            RequirementId removedId,
            RequirementId addedId,
            KnowledgeSnapshotId snapshot1Id,
            KnowledgeSnapshotId snapshot2Id,
            SpecificationVersion version1,
            SpecificationVersion version2,
            Requirement version1Unchanged,
            Requirement version1Modified,
            Requirement version1Removed,
            Requirement version2Modified,
            Requirement version2Added) {
    }

    private record RollbackResult(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId specificationVersionId) {
    }
}