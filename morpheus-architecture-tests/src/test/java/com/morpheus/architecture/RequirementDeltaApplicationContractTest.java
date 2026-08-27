package com.morpheus.architecture;

import com.morpheus.application.delta.AppliedRequirementDelta;
import com.morpheus.application.delta.RequirementDeltaApplicationException;
import com.morpheus.application.delta.RequirementDeltaApplicationPlan;
import com.morpheus.application.delta.RequirementDeltaApplicationResult;
import com.morpheus.application.delta.RequirementDeltaApplicationService;
import com.morpheus.application.delta.RequirementDeltaPromotionResult;
import com.morpheus.application.delta.RequirementDeltaPromotionService;
import com.morpheus.application.delta.RequirementPromotionEvidence;
import com.morpheus.application.snapshot.SnapshotLifecycleService;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.application.temporal.CurrentRequirementQueryService;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementDeltaApplicationContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T08:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryAppliesPromotesAndActivatesExplicitly() {
        var store = new MemorySpecificationKnowledgeStore();
        verifyApplyPromoteActivate(store, store);
    }

    @Test
    void sqliteAppliesPromotesAndActivatesExplicitly() {
        Path database = tempDir.resolve("delta-apply.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            verifyApplyPromoteActivate(snapshots, requirements);
        }
    }

    @Test
    void memoryRejectsAmbiguousOrIncoherentBatchesBeforeWritingCandidate() {
        var store = new MemorySpecificationKnowledgeStore();
        verifyConflictsRejectedBeforeWrites(store, store);
    }

    @Test
    void sqliteRejectsAmbiguousOrIncoherentBatchesBeforeWritingCandidate() {
        Path database = tempDir.resolve("delta-conflicts.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            verifyConflictsRejectedBeforeWrites(snapshots, requirements);
        }
    }

    @Test
    void applicationSemanticsDoNotDependOnDeltaInputOrder() {
        BaselineFixture fixture = fixture();
        var forwardStore = new MemorySpecificationKnowledgeStore();
        var backwardStore = new MemorySpecificationKnowledgeStore();
        seed(forwardStore, forwardStore, fixture);
        seed(backwardStore, backwardStore, fixture);

        RequirementDeltaApplicationResult forward = new RequirementDeltaApplicationService(forwardStore, forwardStore)
                .apply(validPlan(fixture, List.of(fixture.modifiedDelta(), fixture.removedDelta(), fixture.addedDelta())));

        List<RequirementDelta> reversed = new ArrayList<>(List.of(
                fixture.modifiedDelta(), fixture.removedDelta(), fixture.addedDelta()));
        Collections.reverse(reversed);
        RequirementDeltaApplicationResult backward = new RequirementDeltaApplicationService(backwardStore, backwardStore)
                .apply(validPlan(fixture, reversed));

        assertEquals(contentByIdentity(forward.records()), contentByIdentity(backward.records()));
        assertEquals(
                forward.appliedDeltas().stream().map(AppliedRequirementDelta::deltaId).toList(),
                backward.appliedDeltas().stream().map(AppliedRequirementDelta::deltaId).toList());
    }

    @Test
    void memoryFailedPromotionKeepsPreviousBaselineActive() {
        var store = new MemorySpecificationKnowledgeStore();
        verifyFailedPromotionKeepsActive(store, store);
    }

    @Test
    void sqliteFailedPromotionKeepsPreviousBaselineActive() {
        Path database = tempDir.resolve("delta-failed-promotion.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database)) {
            verifyFailedPromotionKeepsActive(snapshots, requirements);
        }
    }

    @Test
    void completedLifecycleDoesNotApplyPromoteOrActivateAnything() {
        var store = new MemorySpecificationKnowledgeStore();
        BaselineFixture fixture = fixture();
        seed(store, store, fixture);
        KnowledgeSnapshotMetadata untouchedCandidate = candidateSnapshot(fixture, KnowledgeSnapshotId.generate());

        ChangeLifecycle completed = ChangeLifecycle.of(fixture.changeId(), ChangeLifecycleState.COMPLETED);

        assertEquals(ChangeLifecycleState.COMPLETED, completed.state());
        assertTrue(store.findSnapshot(untouchedCandidate.id()).isEmpty());
        assertEquals(
                fixture.modifiedCurrent(),
                new CurrentRequirementQueryService(store, store)
                        .current(fixture.projectId(), fixture.modifiedId().value())
                        .orElseThrow());
        assertEquals(fixture.activeSnapshotId(), store.activeSnapshot(fixture.projectId()).orElseThrow().id());
    }

    private void verifyApplyPromoteActivate(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements) {
        BaselineFixture fixture = fixture();
        seed(snapshots, requirements, fixture);
        CurrentRequirementQueryService current = new CurrentRequirementQueryService(snapshots, requirements);
        RequirementDeltaApplicationPlan plan = validPlan(
                fixture,
                List.of(fixture.modifiedDelta(), fixture.removedDelta(), fixture.addedDelta()));

        assertEquals(
                fixture.modifiedCurrent(),
                current.current(fixture.projectId(), fixture.modifiedId().value()).orElseThrow());

        RequirementDeltaApplicationResult applied = new RequirementDeltaApplicationService(snapshots, requirements)
                .apply(plan);

        assertEquals(KnowledgeSnapshotState.BUILDING,
                snapshots.findSnapshot(applied.candidateSnapshot().id()).orElseThrow().state());
        assertEquals(
                fixture.modifiedCurrent(),
                current.current(fixture.projectId(), fixture.modifiedId().value()).orElseThrow(),
                "APPLY must not change CURRENT visibility");
        assertEquals(fixture.activeSnapshotId(), snapshots.activeSnapshot(fixture.projectId()).orElseThrow().id());

        Map<DomainIdentity, RequirementVersionRecord> candidate = recordsByIdentity(applied.records());
        RequirementVersionRecord modified = candidate.get(fixture.modifiedId().value());
        RequirementVersionRecord unchanged = candidate.get(fixture.unchangedId().value());
        RequirementVersionRecord added = candidate.get(fixture.addedId().value());

        assertEquals(3, candidate.size());
        assertEquals(fixture.modifiedId().value(), modified.entityVersion().entityIdentity());
        assertNotEquals(fixture.modifiedCurrent().entityVersion().id(), modified.entityVersion().id());
        assertEquals("retain sessions for 60 minutes", modified.entityVersion().content().statement());
        assertEquals(fixture.modifiedDelta().provenance(), modified.entityVersion().content().provenance());
        assertEquals(fixture.unchangedCurrent().entityVersion().content(), unchanged.entityVersion().content());
        assertEquals("Remember-me opt-in", added.entityVersion().content().title());
        assertFalse(candidate.containsKey(fixture.removedId().value()));

        assertEquals(
                fixture.removedCurrent(),
                requirements.currentRequirement(fixture.activeSnapshotId(), fixture.removedId().value()).orElseThrow(),
                "REMOVED must not delete the ACTIVE occurrence");
        assertTrue(requirements.listRequirementVersions(fixture.activeSnapshotId()).contains(fixture.proposedModified()));
        assertEquals(
                fixture.proposedModified().entityVersion().content().statement(),
                "retain sessions for 15 minutes");

        AppliedRequirementDelta removedReceipt = applied.appliedDeltas().stream()
                .filter(receipt -> receipt.kind() == RequirementDeltaKind.REMOVED)
                .findFirst()
                .orElseThrow();
        assertEquals(fixture.removedDelta().provenance().evidenceId(), removedReceipt.sourceEvidenceId());
        assertTrue(removedReceipt.resultingEntityVersionId().isEmpty());
        assertEquals(plan.applicationEvidenceId(), applied.applicationEvidenceId());

        RequirementPromotionEvidence promotionEvidence = new RequirementPromotionEvidence(
                EvidenceId.generate(),
                "Candidate requirement baseline reviewed and complete",
                T0.plusSeconds(100));
        RequirementDeltaPromotionResult promoted = new RequirementDeltaPromotionService(snapshots, requirements)
                .promote(applied, promotionEvidence);

        assertEquals(KnowledgeSnapshotState.READY, promoted.readySnapshot().state());
        assertEquals(promotionEvidence, promoted.promotionEvidence());
        assertEquals(plan.applicationEvidenceId(), promoted.applicationEvidenceId());
        assertEquals(fixture.activeSnapshotId(), snapshots.activeSnapshot(fixture.projectId()).orElseThrow().id());
        assertEquals(
                fixture.modifiedCurrent(),
                current.current(fixture.projectId(), fixture.modifiedId().value()).orElseThrow(),
                "PROMOTE must not activate the candidate");

        KnowledgeSnapshotMetadata activated = new SnapshotLifecycleService(snapshots).activate(applied.candidateSnapshot().id());

        assertEquals(KnowledgeSnapshotState.ACTIVE, activated.state());
        assertEquals(applied.candidateSnapshot().id(), snapshots.activeSnapshot(fixture.projectId()).orElseThrow().id());
        assertEquals(KnowledgeSnapshotState.RETIRED,
                snapshots.findSnapshot(fixture.activeSnapshotId()).orElseThrow().state());
        assertEquals(
                "retain sessions for 60 minutes",
                current.current(fixture.projectId(), fixture.modifiedId().value())
                        .orElseThrow().entityVersion().content().statement());
        assertTrue(current.current(fixture.projectId(), fixture.removedId().value()).isEmpty());
        assertEquals(
                "Remember-me opt-in",
                current.current(fixture.projectId(), fixture.addedId().value())
                        .orElseThrow().entityVersion().content().title());
    }

    private void verifyConflictsRejectedBeforeWrites(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements) {
        BaselineFixture fixture = fixture();
        seed(snapshots, requirements, fixture);
        RequirementDeltaApplicationService service = new RequirementDeltaApplicationService(snapshots, requirements);

        RequirementDelta duplicateIdentity = new RequirementDelta(
                RequirementDeltaId.generate(),
                fixture.changeId(),
                RequirementDeltaKind.REMOVED,
                "auth-session",
                fixture.modifiedId(),
                Optional.of("session-expiration"),
                "Session expiration",
                Optional.empty(),
                List.of(),
                provenance("changes/duplicate.md", "duplicate-remove"));
        RequirementDeltaApplicationPlan ambiguous = candidatePlan(
                fixture,
                List.of(fixture.modifiedDelta(), duplicateIdentity),
                Map.of());
        assertThrows(RequirementDeltaApplicationException.class, () -> service.apply(ambiguous));
        assertCandidateWasNotWritten(snapshots, requirements, ambiguous);

        RequirementDelta addedExisting = new RequirementDelta(
                RequirementDeltaId.generate(),
                fixture.changeId(),
                RequirementDeltaKind.ADDED,
                "auth-session",
                fixture.modifiedId(),
                Optional.of("session-expiration"),
                "Session expiration duplicate",
                Optional.of("duplicate content must not create a new logical element"),
                List.of(),
                provenance("changes/added-existing.md", "added-existing"));
        RequirementDeltaApplicationPlan illegalAdd = candidatePlan(fixture, List.of(addedExisting), Map.of());
        assertThrows(RequirementDeltaApplicationException.class, () -> service.apply(illegalAdd));
        assertCandidateWasNotWritten(snapshots, requirements, illegalAdd);

        RequirementId missingId = RequirementId.generate();
        RequirementDelta modifiedMissing = new RequirementDelta(
                RequirementDeltaId.generate(),
                fixture.changeId(),
                RequirementDeltaKind.MODIFIED,
                "auth-session",
                missingId,
                Optional.of("missing"),
                "Missing requirement",
                Optional.of("must not be invented"),
                List.of(),
                provenance("changes/missing.md", "modified-missing"));
        RequirementDeltaApplicationPlan illegalModify = candidatePlan(fixture, List.of(modifiedMissing), Map.of());
        assertThrows(RequirementDeltaApplicationException.class, () -> service.apply(illegalModify));
        assertCandidateWasNotWritten(snapshots, requirements, illegalModify);

        assertEquals(fixture.activeSnapshotId(), snapshots.activeSnapshot(fixture.projectId()).orElseThrow().id());
    }

    private void verifyFailedPromotionKeepsActive(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements) {
        BaselineFixture fixture = fixture();
        seed(snapshots, requirements, fixture);
        CurrentRequirementQueryService current = new CurrentRequirementQueryService(snapshots, requirements);
        RequirementDeltaApplicationResult applied = new RequirementDeltaApplicationService(snapshots, requirements)
                .apply(validPlan(fixture, List.of(fixture.modifiedDelta(), fixture.removedDelta(), fixture.addedDelta())));

        RequirementId rogueId = RequirementId.generate();
        Requirement rogue = requirement(
                rogueId,
                fixture.specificationId(),
                "ROGUE",
                "Rogue proposal",
                "must make promotion fail",
                provenance("changes/rogue.md", "rogue"));
        requirements.putRequirementVersion(new RequirementVersionRecord(
                applied.candidateSnapshot().id(),
                new EntityVersion<>(
                        EntityVersionId.generate(),
                        rogueId.value(),
                        applied.specificationVersion().id(),
                        TemporalState.PROPOSED,
                        rogue)));

        RequirementPromotionEvidence promotionEvidence = new RequirementPromotionEvidence(
                EvidenceId.generate(),
                "Attempt promotion with a tampered candidate",
                T0.plusSeconds(200));
        assertThrows(
                RequirementDeltaApplicationException.class,
                () -> new RequirementDeltaPromotionService(snapshots, requirements).promote(applied, promotionEvidence));

        assertEquals(KnowledgeSnapshotState.FAILED,
                snapshots.findSnapshot(applied.candidateSnapshot().id()).orElseThrow().state());
        assertEquals(fixture.activeSnapshotId(), snapshots.activeSnapshot(fixture.projectId()).orElseThrow().id());
        assertEquals(
                fixture.modifiedCurrent(),
                current.current(fixture.projectId(), fixture.modifiedId().value()).orElseThrow());
    }

    private RequirementDeltaApplicationPlan validPlan(
            BaselineFixture fixture,
            List<RequirementDelta> deltas) {
        Map<DomainIdentity, EntityVersionId> entityVersionIds = Map.of(
                fixture.modifiedId().value(), EntityVersionId.generate(),
                fixture.unchangedId().value(), EntityVersionId.generate(),
                fixture.addedId().value(), EntityVersionId.generate());
        return candidatePlan(fixture, deltas, entityVersionIds);
    }

    private RequirementDeltaApplicationPlan candidatePlan(
            BaselineFixture fixture,
            List<RequirementDelta> deltas,
            Map<DomainIdentity, EntityVersionId> entityVersionIds) {
        SpecificationVersion candidateVersion = new SpecificationVersion(
                SpecificationVersionId.generate(),
                fixture.projectId(),
                Optional.of(2L),
                Optional.of("test-provider-v2"),
                Optional.of("source-revision-2"),
                T0.plusSeconds(10),
                Optional.of(fixture.activeVersion().id()));
        KnowledgeSnapshotMetadata candidateSnapshot = candidateSnapshot(fixture, KnowledgeSnapshotId.generate());
        return new RequirementDeltaApplicationPlan(
                fixture.projectId(),
                candidateVersion,
                candidateSnapshot,
                deltas,
                Map.of("auth-session", fixture.specificationId()),
                entityVersionIds,
                EvidenceId.generate());
    }

    private KnowledgeSnapshotMetadata candidateSnapshot(BaselineFixture fixture, KnowledgeSnapshotId candidateId) {
        return new KnowledgeSnapshotMetadata(
                candidateId,
                fixture.projectId(),
                Optional.of(fixture.activeSnapshotId()),
                KnowledgeSnapshotState.BUILDING,
                Optional.of("source-revision-2"),
                T0.plusSeconds(10));
    }

    private void seed(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            BaselineFixture fixture) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace")));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                fixture.activeSnapshotId(),
                fixture.projectId(),
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("source-revision-1"),
                T0));
        requirements.putSpecificationVersion(fixture.activeVersion());
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.activeSnapshotId(),
                fixture.activeVersion().id()));
        requirements.putRequirementVersion(fixture.modifiedCurrent());
        requirements.putRequirementVersion(fixture.removedCurrent());
        requirements.putRequirementVersion(fixture.unchangedCurrent());
        requirements.putRequirementVersion(fixture.proposedModified());
        snapshots.activateSnapshot(fixture.activeSnapshotId(), Optional.empty());
    }

    private BaselineFixture fixture() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        SpecificationVersion activeVersion = new SpecificationVersion(
                SpecificationVersionId.generate(),
                projectId,
                Optional.of(1L),
                Optional.of("test-provider-v1"),
                Optional.of("source-revision-1"),
                T0,
                Optional.empty());
        KnowledgeSnapshotId activeSnapshotId = KnowledgeSnapshotId.generate();
        RequirementId modifiedId = RequirementId.generate();
        RequirementId removedId = RequirementId.generate();
        RequirementId unchangedId = RequirementId.generate();
        RequirementId addedId = RequirementId.generate();
        ChangeId changeId = ChangeId.generate();

        RequirementVersionRecord modifiedCurrent = currentRecord(
                activeSnapshotId,
                activeVersion.id(),
                requirement(
                        modifiedId,
                        specificationId,
                        "SESSION_EXPIRATION",
                        "Session expiration",
                        "retain sessions for 30 minutes",
                        provenance("specs/auth-session.md", "baseline-modified")));
        RequirementVersionRecord removedCurrent = currentRecord(
                activeSnapshotId,
                activeVersion.id(),
                requirement(
                        removedId,
                        specificationId,
                        "LEGACY_TOKEN",
                        "Legacy token",
                        "legacy tokens are accepted",
                        provenance("specs/auth-session.md", "baseline-removed")));
        RequirementVersionRecord unchangedCurrent = currentRecord(
                activeSnapshotId,
                activeVersion.id(),
                requirement(
                        unchangedId,
                        specificationId,
                        "PASSWORD_RULE",
                        "Password rule",
                        "passwords require twelve characters",
                        provenance("specs/auth-session.md", "baseline-unchanged")));
        RequirementVersionRecord proposedModified = new RequirementVersionRecord(
                activeSnapshotId,
                new EntityVersion<>(
                        EntityVersionId.generate(),
                        modifiedId.value(),
                        activeVersion.id(),
                        TemporalState.PROPOSED,
                        requirement(
                                modifiedId,
                                specificationId,
                                "SESSION_EXPIRATION",
                                "Session expiration",
                                "retain sessions for 15 minutes",
                                provenance("changes/other-proposal.md", "other-proposal"))));

        RequirementDelta modifiedDelta = new RequirementDelta(
                RequirementDeltaId.generate(),
                changeId,
                RequirementDeltaKind.MODIFIED,
                "auth-session",
                modifiedId,
                Optional.of("SESSION_EXPIRATION"),
                "Session expiration",
                Optional.of("retain sessions for 60 minutes"),
                List.of(),
                provenance("changes/session-expiration.md", "delta-modified"));
        RequirementDelta removedDelta = new RequirementDelta(
                RequirementDeltaId.generate(),
                changeId,
                RequirementDeltaKind.REMOVED,
                "auth-session",
                removedId,
                Optional.of("LEGACY_TOKEN"),
                "Legacy token",
                Optional.empty(),
                List.of(),
                provenance("changes/legacy-token.md", "delta-removed"));
        RequirementDelta addedDelta = new RequirementDelta(
                RequirementDeltaId.generate(),
                changeId,
                RequirementDeltaKind.ADDED,
                "auth-session",
                addedId,
                Optional.of("REMEMBER_ME_OPT_IN"),
                "Remember-me opt-in",
                Optional.of("remember-me requires explicit opt-in"),
                List.of(),
                provenance("changes/remember-me.md", "delta-added"));

        return new BaselineFixture(
                projectId,
                specificationId,
                activeVersion,
                activeSnapshotId,
                modifiedId,
                removedId,
                unchangedId,
                addedId,
                changeId,
                modifiedCurrent,
                removedCurrent,
                unchangedCurrent,
                proposedModified,
                modifiedDelta,
                removedDelta,
                addedDelta);
    }

    private RequirementVersionRecord currentRecord(
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

    private Requirement requirement(
            RequirementId id,
            SpecificationId specificationId,
            String key,
            String title,
            String statement,
            Provenance provenance) {
        return new Requirement(id, specificationId, Optional.of(key), title, statement, provenance);
    }

    private Provenance provenance(String source, String externalId) {
        return new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1"),
                SourceLocator.file(source),
                Optional.of(externalId),
                Optional.of("source-revision"),
                EvidenceId.generate());
    }

    private Map<DomainIdentity, RequirementVersionRecord> recordsByIdentity(List<RequirementVersionRecord> records) {
        Map<DomainIdentity, RequirementVersionRecord> indexed = new HashMap<>();
        records.forEach(record -> indexed.put(record.entityVersion().entityIdentity(), record));
        return indexed;
    }

    private Map<DomainIdentity, Requirement> contentByIdentity(List<RequirementVersionRecord> records) {
        Map<DomainIdentity, Requirement> indexed = new HashMap<>();
        records.forEach(record -> indexed.put(record.entityVersion().entityIdentity(), record.entityVersion().content()));
        return indexed;
    }

    private void assertCandidateWasNotWritten(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            RequirementDeltaApplicationPlan plan) {
        assertTrue(snapshots.findSnapshot(plan.candidateSnapshot().id()).isEmpty());
        assertTrue(requirements.findSpecificationVersion(plan.specificationVersion().id()).isEmpty());
        assertTrue(requirements.listRequirementVersions(plan.candidateSnapshot().id()).isEmpty());
    }

    private record BaselineFixture(
            ProjectSpecificationId projectId,
            SpecificationId specificationId,
            SpecificationVersion activeVersion,
            KnowledgeSnapshotId activeSnapshotId,
            RequirementId modifiedId,
            RequirementId removedId,
            RequirementId unchangedId,
            RequirementId addedId,
            ChangeId changeId,
            RequirementVersionRecord modifiedCurrent,
            RequirementVersionRecord removedCurrent,
            RequirementVersionRecord unchangedCurrent,
            RequirementVersionRecord proposedModified,
            RequirementDelta modifiedDelta,
            RequirementDelta removedDelta,
            RequirementDelta addedDelta) {
    }
}
