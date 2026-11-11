package com.morpheus.application.delta;

import com.morpheus.application.store.InMemorySnapshotStores;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * APPLY builds one BUILDING candidate from the ACTIVE baseline and explicit deltas, and PROMOTE moves exactly that
 * candidate to READY without activating it. Every inconsistency between the plan and the baseline is refused by name,
 * before anything is written.
 */
class RequirementDeltaApplicationServiceTest {
    private static final Instant AT = Instant.parse("2026-10-30T08:00:00Z");
    private static final String SPEC_KEY = "auth";

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final SpecificationId specificationId = SpecificationId.generate();
    private final ChangeId changeId = ChangeId.generate();
    private final EvidenceId evidenceId = EvidenceId.generate();
    private final InMemorySnapshotStores stores = new InMemorySnapshotStores();
    private final RequirementDeltaApplicationService application = new RequirementDeltaApplicationService(stores, stores);
    private final RequirementDeltaPromotionService promotion = new RequirementDeltaPromotionService(stores, stores);

    private final KnowledgeSnapshotMetadata active = new KnowledgeSnapshotMetadata(
            KnowledgeSnapshotId.generate(), projectId, Optional.empty(), KnowledgeSnapshotState.ACTIVE,
            Optional.of("rev-1"), AT);
    private final SpecificationVersion activeVersion = new SpecificationVersion(
            SpecificationVersionId.generate(), projectId, Optional.of(1L), Optional.empty(), Optional.of("rev-1"),
            AT, Optional.empty());
    private final Requirement kept = requirement(RequirementId.generate(), "Kept", "Kept statement.");
    private final Requirement modified = requirement(RequirementId.generate(), "Modified", "Old statement.");
    private final Requirement removed = requirement(RequirementId.generate(), "Removed", "Removed statement.");
    private final RequirementId addedId = RequirementId.generate();
    private final EntityVersionId keptActiveVersion = EntityVersionId.generate();

    private final SpecificationVersion candidateVersion = new SpecificationVersion(
            SpecificationVersionId.generate(), projectId, Optional.of(2L), Optional.empty(), Optional.of("rev-2"),
            AT, Optional.of(activeVersion.id()));
    private final KnowledgeSnapshotMetadata candidate = new KnowledgeSnapshotMetadata(
            KnowledgeSnapshotId.generate(), projectId, Optional.of(active.id()), KnowledgeSnapshotState.BUILDING,
            Optional.of("rev-2"), AT);

    @Test
    void applyBuildsTheCandidateBaselineAndPromoteMakesItReadyWithoutActivatingIt() {
        publishActiveBaseline();
        Map<DomainIdentity, EntityVersionId> versions = candidateVersions();

        RequirementDeltaApplicationResult applied = application.apply(plan(standardDeltas(), versions));

        assertEquals(candidate, applied.candidateSnapshot());
        assertEquals(List.of(kept.id(), modified.id(), addedId).stream().sorted().toList(),
                applied.records().stream().map(record -> record.entityVersion().content().id()).sorted().toList());
        Requirement modifiedInCandidate = applied.records().stream()
                .map(record -> record.entityVersion().content())
                .filter(requirement -> requirement.id().equals(modified.id())).findFirst().orElseThrow();
        assertEquals("New statement.", modifiedInCandidate.statement());
        assertTrue(applied.records().stream().allMatch(record ->
                record.snapshotId().equals(candidate.id())
                        && record.entityVersion().specificationVersionId().equals(candidateVersion.id())
                        && record.entityVersion().temporalState() == TemporalState.CURRENT));
        AppliedRequirementDelta removal = applied.appliedDeltas().stream()
                .filter(receipt -> receipt.kind() == RequirementDeltaKind.REMOVED).findFirst().orElseThrow();
        assertEquals(Optional.empty(), removal.resultingEntityVersionId());
        assertEquals(removed.id(), removal.requirementId());
        assertEquals(evidenceId, removal.sourceEvidenceId());
        AppliedRequirementDelta addition = applied.appliedDeltas().stream()
                .filter(receipt -> receipt.kind() == RequirementDeltaKind.ADDED).findFirst().orElseThrow();
        assertEquals(Optional.of(versions.get(addedId.value())), addition.resultingEntityVersionId());
        assertEquals(Optional.of(new SnapshotSpecificationVersionBinding(candidate.id(), candidateVersion.id())),
                stores.findSnapshotVersion(candidate.id()));
        assertEquals(KnowledgeSnapshotState.BUILDING, stores.findSnapshot(candidate.id()).orElseThrow().state());

        RequirementPromotionEvidence decision = new RequirementPromotionEvidence(EvidenceId.generate(), " reviewed ", AT);
        RequirementDeltaPromotionResult promoted = promotion.promote(applied, decision);

        assertEquals(KnowledgeSnapshotState.READY, promoted.readySnapshot().state());
        assertEquals(candidateVersion.id(), promoted.specificationVersionId());
        assertEquals(applied.applicationEvidenceId(), promoted.applicationEvidenceId());
        assertEquals("reviewed", promoted.promotionEvidence().rationale());
        assertEquals(active.id(), stores.activeSnapshot(projectId).orElseThrow().id(),
                "promotion never activates the candidate");
    }

    @Test
    void anEmptyPlanIsRefusedBeforeAnyRead() {
        RequirementDeltaApplicationPlan plan = plan(List.of(), Map.of());

        assertRefused("explicit delta application requires at least one delta", plan);
    }

    @Test
    void aProjectWithoutAnActiveSnapshotIsRefused() {
        RequirementDeltaApplicationPlan plan = plan(standardDeltas(), candidateVersions());

        assertRefused("project has no ACTIVE snapshot: " + projectId, plan);
    }

    @Test
    void anActiveSnapshotWithoutAVersionBindingIsRefused() {
        stores.putSnapshot(active);
        RequirementDeltaApplicationPlan plan = plan(standardDeltas(), candidateVersions());

        assertRefused("ACTIVE snapshot has no specification version binding: " + active.id(), plan);
    }

    @Test
    void anActiveBindingToAnUnknownVersionIsRefused() {
        stores.putSnapshot(active);
        stores.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(active.id(), activeVersion.id()));
        RequirementDeltaApplicationPlan plan = plan(standardDeltas(), candidateVersions());

        assertRefused("ACTIVE specification version not found: " + activeVersion.id(), plan);
    }

    @Test
    void aCandidateThatDoesNotFollowTheActiveBaselineIsRefused() {
        publishActiveBaseline();
        SpecificationVersion orphanVersion = new SpecificationVersion(
                SpecificationVersionId.generate(), projectId, Optional.of(2L), Optional.empty(), Optional.empty(),
                AT, Optional.empty());
        KnowledgeSnapshotMetadata ready = candidate.withState(KnowledgeSnapshotState.READY);
        KnowledgeSnapshotMetadata detached = new KnowledgeSnapshotMetadata(
                candidate.id(), projectId, Optional.empty(), KnowledgeSnapshotState.BUILDING, Optional.empty(), AT);

        assertRefused("candidate specification version predecessor must be the ACTIVE specification version",
                plan(orphanVersion, candidate, standardDeltas(), candidateVersions()));
        assertRefused("candidate snapshot must start in BUILDING",
                plan(candidateVersion, ready, standardDeltas(), candidateVersions()));
        assertRefused("candidate snapshot predecessor must be the ACTIVE snapshot",
                plan(candidateVersion, detached, standardDeltas(), candidateVersions()));
        assertRefused("candidate snapshot must be distinct from the ACTIVE snapshot",
                plan(candidateVersion, new KnowledgeSnapshotMetadata(active.id(), projectId, Optional.of(active.id()),
                        KnowledgeSnapshotState.BUILDING, Optional.empty(), AT), standardDeltas(), candidateVersions()));
    }

    @Test
    void aCandidateOfAnotherProjectIsRefused() {
        publishActiveBaseline();
        ProjectSpecificationId other = ProjectSpecificationId.generate();
        SpecificationVersion foreignVersion = new SpecificationVersion(
                SpecificationVersionId.generate(), other, Optional.empty(), Optional.empty(), Optional.empty(),
                AT, Optional.of(activeVersion.id()));
        KnowledgeSnapshotMetadata foreignSnapshot = new KnowledgeSnapshotMetadata(
                KnowledgeSnapshotId.generate(), other, Optional.of(active.id()), KnowledgeSnapshotState.BUILDING,
                Optional.empty(), AT);

        assertRefused("candidate specification version belongs to another project",
                plan(foreignVersion, candidate, standardDeltas(), candidateVersions()));
        assertRefused("candidate snapshot belongs to another project",
                plan(candidateVersion, foreignSnapshot, standardDeltas(), candidateVersions()));
    }

    @Test
    void deltasThatContradictTheBaselineAreRefusedByName() {
        publishActiveBaseline();
        RequirementId absent = RequirementId.generate();

        assertRefused("ADDED requirement already exists in ACTIVE baseline: " + kept.id(),
                plan(List.of(delta(RequirementDeltaKind.ADDED, kept.id(), Optional.of("Again."))), Map.of()));
        assertRefused("MODIFIED requirement is absent from ACTIVE baseline: " + absent,
                plan(List.of(delta(RequirementDeltaKind.MODIFIED, absent, Optional.of("New."))), Map.of()));
        RequirementDelta unmapped = new RequirementDelta(RequirementDeltaId.generate(), changeId,
                RequirementDeltaKind.REMOVED, "unknown-spec", removed.id(), Optional.empty(), "Removed",
                Optional.empty(), List.of(), provenance());
        assertRefused("no explicit SpecificationId mapping for specificationKey: unknown-spec",
                plan(List.of(unmapped), Map.of()));
    }

    @Test
    void aDeltaPointingAtAnotherSpecificationIsRefused() {
        publishActiveBaseline();
        RequirementDelta delta = delta(RequirementDeltaKind.REMOVED, removed.id(), Optional.empty());
        RequirementDeltaApplicationPlan plan = new RequirementDeltaApplicationPlan(projectId, candidateVersion,
                candidate, List.of(delta), Map.of(SPEC_KEY, SpecificationId.generate()), Map.of(), evidenceId);

        assertRefused("delta specificationKey resolves to a different specification for " + removed.id(), plan);
    }

    @Test
    void anAmbiguousBatchIsRefused() {
        publishActiveBaseline();
        RequirementDelta first = delta(RequirementDeltaKind.MODIFIED, modified.id(), Optional.of("One."));
        RequirementDelta second = delta(RequirementDeltaKind.REMOVED, modified.id(), Optional.empty());

        assertRefused("duplicate RequirementDeltaId in application batch: " + first.id(),
                plan(List.of(first, first), Map.of()));
        assertRefused("multiple deltas for one logical requirement are ambiguous: " + modified.id(),
                plan(List.of(first, second), Map.of()));
    }

    @Test
    void theEntityVersionPlanMustNameExactlyTheResultingRequirementsWithFreshIdentities() {
        publishActiveBaseline();
        Map<DomainIdentity, EntityVersionId> missingOne = Map.of(
                kept.id().value(), EntityVersionId.generate(), modified.id().value(), EntityVersionId.generate());
        EntityVersionId shared = EntityVersionId.generate();
        Map<DomainIdentity, EntityVersionId> duplicated = Map.of(
                kept.id().value(), shared, modified.id().value(), shared, addedId.value(), EntityVersionId.generate());
        Map<DomainIdentity, EntityVersionId> reused = Map.of(
                kept.id().value(), keptActiveVersion, modified.id().value(), EntityVersionId.generate(),
                addedId.value(), EntityVersionId.generate());

        RequirementDeltaApplicationException mismatch = assertThrows(RequirementDeltaApplicationException.class,
                () -> application.apply(plan(standardDeltas(), missingOne)));
        assertTrue(mismatch.getMessage().startsWith(
                "candidate EntityVersionId plan must match resulting identities exactly; missing=[" + addedId.value()),
                mismatch.getMessage());
        assertRefused("candidate EntityVersionId values must be unique", plan(standardDeltas(), duplicated));
        assertRefused("candidate must create new EntityVersionId values instead of reusing ACTIVE occurrence "
                + keptActiveVersion, plan(standardDeltas(), reused));
    }

    @Test
    void aCandidateAlreadyHoldingAForeignOccurrenceIsRefused() {
        publishActiveBaseline();
        EntityVersionId foreign = EntityVersionId.generate();
        stores.putRequirementVersion(new RequirementVersionRecord(candidate.id(), new EntityVersion<>(
                foreign, kept.id().value(), candidateVersion.id(), TemporalState.CURRENT, kept)));

        assertRefused("candidate snapshot already contains a requirement occurrence outside this application plan: "
                + foreign, plan(standardDeltas(), candidateVersions()));
    }

    @Test
    void promotionRefusesACandidateThatIsUnknownOrNoLongerBuilding() {
        publishActiveBaseline();
        RequirementDeltaApplicationResult applied = application.apply(plan(standardDeltas(), candidateVersions()));
        RequirementPromotionEvidence decision = new RequirementPromotionEvidence(EvidenceId.generate(), "ok", AT);
        RequirementDeltaApplicationResult unknown = new RequirementDeltaApplicationResult(candidateVersion,
                new KnowledgeSnapshotMetadata(KnowledgeSnapshotId.generate(), projectId, Optional.of(active.id()),
                        KnowledgeSnapshotState.BUILDING, Optional.empty(), AT),
                applied.records(), applied.appliedDeltas(), evidenceId);
        promotion.promote(applied, decision);

        RequirementDeltaApplicationException missing = assertThrows(RequirementDeltaApplicationException.class,
                () -> promotion.promote(unknown, decision));
        RequirementDeltaApplicationException twice = assertThrows(RequirementDeltaApplicationException.class,
                () -> promotion.promote(applied, decision));

        assertEquals("candidate snapshot not found: " + unknown.candidateSnapshot().id(), missing.getMessage());
        assertEquals("only a BUILDING candidate can be promoted explicitly; actual=READY", twice.getMessage());
    }

    @Test
    void promotionRefusesAReceiptThatNoLongerMatchesWhatWasPersisted() {
        publishActiveBaseline();
        RequirementDeltaApplicationResult applied = application.apply(plan(standardDeltas(), candidateVersions()));
        RequirementPromotionEvidence decision = new RequirementPromotionEvidence(EvidenceId.generate(), "ok", AT);
        RequirementDeltaApplicationResult otherDefinition = new RequirementDeltaApplicationResult(candidateVersion,
                new KnowledgeSnapshotMetadata(candidate.id(), projectId, Optional.of(active.id()),
                        KnowledgeSnapshotState.BUILDING, Optional.of("rev-other"), AT),
                applied.records(), applied.appliedDeltas(), evidenceId);
        SpecificationVersion otherVersion = new SpecificationVersion(
                SpecificationVersionId.generate(), projectId, Optional.empty(), Optional.empty(), Optional.empty(),
                AT, Optional.of(activeVersion.id()));
        RequirementDeltaApplicationResult otherBinding = new RequirementDeltaApplicationResult(otherVersion,
                candidate, applied.records(), applied.appliedDeltas(), evidenceId);

        RequirementDeltaApplicationException definition = assertThrows(RequirementDeltaApplicationException.class,
                () -> promotion.promote(otherDefinition, decision));
        RequirementDeltaApplicationException binding = assertThrows(RequirementDeltaApplicationException.class,
                () -> promotion.promote(otherBinding, decision));

        assertEquals("candidate snapshot definition differs from APPLY receipt", definition.getMessage());
        assertEquals("candidate snapshot binding differs from APPLY receipt", binding.getMessage());
    }

    @Test
    void aCandidateWhoseProjectionDriftedAfterApplyFailsPromotionAndIsLeftFailed() {
        publishActiveBaseline();
        RequirementDeltaApplicationResult applied = application.apply(plan(standardDeltas(), candidateVersions()));
        stores.putRequirementVersion(new RequirementVersionRecord(candidate.id(), new EntityVersion<>(
                EntityVersionId.generate(), removed.id().value(), activeVersion.id(), TemporalState.CURRENT, removed)));
        RequirementPromotionEvidence decision = new RequirementPromotionEvidence(EvidenceId.generate(), "ok", AT);

        RequirementDeltaApplicationException refusal = assertThrows(RequirementDeltaApplicationException.class,
                () -> promotion.promote(applied, decision));

        assertEquals("candidate projection failed promotion validation and is now FAILED", refusal.getMessage());
        assertEquals(KnowledgeSnapshotState.FAILED, stores.findSnapshot(candidate.id()).orElseThrow().state());
    }

    @Test
    void theReceiptsRefuseInconsistentShapes() {
        RequirementDeltaId deltaId = RequirementDeltaId.generate();
        RequirementId requirementId = RequirementId.generate();

        IllegalArgumentException removedWithVersion = assertThrows(IllegalArgumentException.class,
                () -> new AppliedRequirementDelta(deltaId, changeId, RequirementDeltaKind.REMOVED, requirementId,
                        evidenceId, Optional.of(EntityVersionId.generate())));
        IllegalArgumentException addedWithout = assertThrows(IllegalArgumentException.class,
                () -> new AppliedRequirementDelta(deltaId, changeId, RequirementDeltaKind.ADDED, requirementId,
                        evidenceId, Optional.empty()));
        IllegalArgumentException blankKey = assertThrows(IllegalArgumentException.class,
                () -> new RequirementDeltaApplicationPlan(projectId, candidateVersion, candidate, List.of(),
                        Map.of(" ", specificationId), Map.of(), evidenceId));
        IllegalArgumentException blankRationale = assertThrows(IllegalArgumentException.class,
                () -> new RequirementPromotionEvidence(evidenceId, "  ", AT));

        assertEquals("REMOVED delta must not expose a resulting entity version", removedWithVersion.getMessage());
        assertEquals("ADDED delta must expose its resulting entity version", addedWithout.getMessage());
        assertEquals("specificationIdsByKey must not contain blank keys", blankKey.getMessage());
        assertEquals("rationale must not be blank", blankRationale.getMessage());
    }

    private void assertRefused(String message, RequirementDeltaApplicationPlan plan) {
        RequirementDeltaApplicationException refusal = assertThrows(RequirementDeltaApplicationException.class,
                () -> application.apply(plan));
        assertEquals(message, refusal.getMessage());
    }

    private void publishActiveBaseline() {
        stores.putSnapshot(active);
        stores.putSpecificationVersion(activeVersion);
        stores.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(active.id(), activeVersion.id()));
        stores.putRequirementVersion(record(keptActiveVersion, kept, TemporalState.CURRENT));
        stores.putRequirementVersion(record(EntityVersionId.generate(), modified, TemporalState.CURRENT));
        stores.putRequirementVersion(record(EntityVersionId.generate(), removed, TemporalState.CURRENT));
    }

    private RequirementVersionRecord record(EntityVersionId id, Requirement requirement, TemporalState state) {
        return new RequirementVersionRecord(active.id(), new EntityVersion<>(
                id, requirement.id().value(), activeVersion.id(), state, requirement));
    }

    private List<RequirementDelta> standardDeltas() {
        return List.of(
                delta(RequirementDeltaKind.MODIFIED, modified.id(), Optional.of("New statement.")),
                delta(RequirementDeltaKind.REMOVED, removed.id(), Optional.empty()),
                delta(RequirementDeltaKind.ADDED, addedId, Optional.of("Added statement.")));
    }

    private Map<DomainIdentity, EntityVersionId> candidateVersions() {
        return Map.of(
                kept.id().value(), EntityVersionId.generate(),
                modified.id().value(), EntityVersionId.generate(),
                addedId.value(), EntityVersionId.generate());
    }

    private RequirementDeltaApplicationPlan plan(
            List<RequirementDelta> deltas,
            Map<DomainIdentity, EntityVersionId> versions) {
        return plan(candidateVersion, candidate, deltas, versions);
    }

    private RequirementDeltaApplicationPlan plan(
            SpecificationVersion version,
            KnowledgeSnapshotMetadata snapshot,
            List<RequirementDelta> deltas,
            Map<DomainIdentity, EntityVersionId> versions) {
        return new RequirementDeltaApplicationPlan(
                projectId, version, snapshot, deltas, Map.of(SPEC_KEY, specificationId), versions, evidenceId);
    }

    private RequirementDelta delta(RequirementDeltaKind kind, RequirementId requirementId, Optional<String> statement) {
        return new RequirementDelta(RequirementDeltaId.generate(), changeId, kind, SPEC_KEY, requirementId,
                Optional.empty(), "Title", statement, List.of(), provenance());
    }

    private Requirement requirement(RequirementId id, String title, String statement) {
        return new Requirement(id, specificationId, Optional.empty(), title, statement, provenance());
    }

    private Provenance provenance() {
        return new Provenance(new ProviderId("test-provider"), Optional.empty(), SourceLocator.file("specs/a.md"),
                Optional.empty(), Optional.empty(), evidenceId);
    }
}
