package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.lifecycle.ChangeLifecycleStateMachine;
import com.morpheus.application.quality.ChangeCompletenessAssessment;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.ChangeLifecycleQualityAssessment;
import com.morpheus.application.quality.ChangeLifecycleQualityService;
import com.morpheus.application.quality.QualityFactValue;
import com.morpheus.application.quality.QualityFindingCode;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingPolicy;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;
import com.morpheus.domain.evidence.Evidence;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A change with nothing ingested has observed nothing; that is not a fact about the change.
 *
 * <p>{@code allMatch} is true of an empty stream, so a change with no constraint at all asserted
 * {@code criticalConstraintsKnown = TRUE}, the affirmative fact, while a change with one UNKNOWN constraint was
 * blocked. The acceptance-criteria count ignored criteria attached to the change's requirements and then turned
 * an empty count into a definitive FALSE even when the requirement link needed to look for them was itself
 * absent.</p>
 */
class ChangeCompletenessAbsentObservationContractTest {
    private static final Instant T0 = Instant.parse("2026-09-25T10:00:00Z");

    private final Scenario scenario = Scenario.build();

    @Test
    void aChangeWithNoConstraintDoesNotAssertThatItsCriticalConstraintsAreKnown() {
        assertEquals(QualityFactValue.UNAVAILABLE, facts(scenario.noConstraints).criticalConstraintsKnown());
    }

    @Test
    void aChangeWhoseConstraintsAreAllKnownStillAssertsIt() {
        assertEquals(QualityFactValue.TRUE, facts(scenario.knownConstraints).criticalConstraintsKnown());
    }

    @Test
    void aChangeWithOneUnknownConstraintStaysUnavailable() {
        assertEquals(QualityFactValue.UNAVAILABLE, facts(scenario.unknownConstraint).criticalConstraintsKnown());
    }

    @Test
    void theEmptyChangeCannotBeEvaluatedForSpecifiedRatherThanPassingIt() {
        ChangeLifecycleQualityAssessment result = lifecycle().assessDerivedActive(
                        scenario.projectId,
                        ChangeLifecycle.of(scenario.noConstraints.id(), ChangeLifecycleState.PROPOSED),
                        ChangeLifecycleState.SPECIFIED,
                        ChangeLifecyclePolicy.forwardOnly(),
                        Optional.empty())
                .orElseThrow();

        assertTrue(result.unavailableRequiredFacts().contains("criticalConstraintsKnown"), result.unavailableRequiredFacts().toString());
        assertTrue(result.decision().isEmpty());
        assertTrue(result.findings().stream().anyMatch(finding ->
                finding.code() == QualityFindingCode.LIFECYCLE_REQUIRED_FACT_UNAVAILABLE));
    }

    @Test
    void criteriaAttachedToTheChangeRequirementsCountAndLetTheTransitionThrough() {
        assertEquals(QualityFactValue.TRUE, facts(scenario.knownConstraints).acceptanceCriteriaDefined());

        ChangeLifecycleQualityAssessment result = lifecycle().assessDerivedActive(
                        scenario.projectId,
                        ChangeLifecycle.of(scenario.knownConstraints.id(), ChangeLifecycleState.PROPOSED),
                        ChangeLifecycleState.SPECIFIED,
                        ChangeLifecyclePolicy.forwardOnly(),
                        Optional.empty())
                .orElseThrow();

        assertTrue(result.unavailableRequiredFacts().isEmpty(), result.unavailableRequiredFacts().toString());
        assertTrue(result.decision().orElseThrow().allowed(), result.decision().toString());
    }

    @Test
    void aCriterionAttachedToTheChangeItselfStillCounts() {
        assertEquals(QualityFactValue.TRUE, facts(scenario.changeCriterionOnly).acceptanceCriteriaDefined());
    }

    @Test
    void noCriterionAtAllOnAChangeWhoseRequirementsAreKnownIsAnObservedFalse() {
        assertEquals(QualityFactValue.FALSE, facts(scenario.noCriteria).acceptanceCriteriaDefined());
    }

    @Test
    void noRequirementLinkMeansCriteriaCannotBeEnumeratedSoTheFactIsUnavailableNotFalse() {
        assertEquals(QualityFactValue.UNAVAILABLE, facts(scenario.unlinked).acceptanceCriteriaDefined());
    }

    private com.morpheus.application.quality.ChangeLifecycleFactAssessment facts(ChangeProposal change) {
        ChangeCompletenessAssessment assessment = completeness().assessActive(scenario.projectId).orElseThrow()
                .changes().stream().filter(item -> item.change().id().equals(change.id())).findFirst().orElseThrow();
        return assessment.lifecycleFacts();
    }

    private ChangeCompletenessService completeness() {
        return new ChangeCompletenessService(scenario.core, scenario.content, scenario.core, scenario.traceability);
    }

    private ChangeLifecycleQualityService lifecycle() {
        return new ChangeLifecycleQualityService(scenario.core, completeness(), new ChangeLifecycleStateMachine());
    }

    private static final class Scenario {
        final MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        final MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        final MemoryTraceabilityStore traceability = new MemoryTraceabilityStore(core);
        final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ChangeProposal noConstraints;
        ChangeProposal knownConstraints;
        ChangeProposal unknownConstraint;
        ChangeProposal changeCriterionOnly;
        ChangeProposal noCriteria;
        ChangeProposal unlinked;

        static Scenario build() {
            Scenario scenario = new Scenario();
            scenario.seed();
            return scenario;
        }

        private void seed() {
            KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
            SpecificationVersionId versionId = SpecificationVersionId.generate();
            Evidence evidence = new Evidence(
                    EvidenceId.generate(), SourceLocator.file("specs/a.md"), Optional.empty(), Optional.of("sha256:a"));
            Provenance provenance = new Provenance(
                    new ProviderId("qlt-fixture"), Optional.of("1"), SourceLocator.file("specs/a.md"),
                    Optional.of("A"), Optional.of("rev"), evidence.id());
            SpecificationId specificationId = SpecificationId.generate();
            Specification specification = new Specification(
                    specificationId, projectId, "a", "A", Optional.empty(), provenance);
            RequirementId requirementId = RequirementId.generate();
            Requirement requirement = new Requirement(
                    requirementId, specificationId, Optional.of("REQ-A"), "Req", "Req statement", provenance);
            RequirementId bareRequirementId = RequirementId.generate();
            Requirement bareRequirement = new Requirement(
                    bareRequirementId, specificationId, Optional.of("REQ-B"), "Bare", "Bare statement", provenance);

            noConstraints = change(projectId, "No constraints", provenance);
            knownConstraints = change(projectId, "Known constraints", provenance);
            unknownConstraint = change(projectId, "Unknown constraint", provenance);
            changeCriterionOnly = change(projectId, "Change criterion only", provenance);
            noCriteria = change(projectId, "No criteria", provenance);
            unlinked = change(projectId, "Unlinked", provenance);

            List<Constraint> constraints = List.of(
                    known(knownConstraints.id(), provenance),
                    new Constraint(ConstraintId.generate(), unknownConstraint.id(), "Unknown", provenance),
                    known(changeCriterionOnly.id(), provenance));
            List<AcceptanceCriterion> criteria = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                criteria.add(new AcceptanceCriterion(
                        AcceptanceCriterionId.generate(), Optional.of(requirementId), Optional.empty(),
                        "Criterion " + index, "Condition " + index, VerificationStatus.NOT_VERIFIED, List.of(),
                        provenance));
            }
            criteria.add(new AcceptanceCriterion(
                    AcceptanceCriterionId.generate(), Optional.empty(), Optional.of(changeCriterionOnly.id()),
                    "Own", "Own condition", VerificationStatus.NOT_VERIFIED, List.of(), provenance));

            core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-qlt")));
            core.putSpecificationVersion(new SpecificationVersion(
                    versionId, projectId, Optional.of(1L), Optional.of("provider-v1"), Optional.of("revision-1"),
                    T0, Optional.empty()));
            core.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY, Optional.of("revision-1"), T0));
            core.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
            core.putRequirementVersion(new RequirementVersionRecord(
                    snapshotId,
                    new EntityVersion<>(
                            EntityVersionId.generate(), requirementId.value(), versionId, TemporalState.CURRENT,
                            requirement)));
            core.putRequirementVersion(new RequirementVersionRecord(
                    snapshotId,
                    new EntityVersion<>(
                            EntityVersionId.generate(), bareRequirementId.value(), versionId, TemporalState.CURRENT,
                            bareRequirement)));
            content.putSnapshotContent(new SnapshotBusinessContent(
                    snapshotId, versionId, List.of(specification), List.of(),
                    List.of(noConstraints, knownConstraints, unknownConstraint, changeCriterionOnly, noCriteria, unlinked),
                    constraints, List.of(), List.of(), criteria, List.of(evidence)));
            for (ChangeProposal linked : List.of(noConstraints, knownConstraints, unknownConstraint)) {
                traceability.putLink(snapshotId, affects(linked.id(), requirementId, evidence.id()));
            }
            for (ChangeProposal linked : List.of(changeCriterionOnly, noCriteria)) {
                traceability.putLink(snapshotId, affects(linked.id(), bareRequirementId, evidence.id()));
            }
            core.activateSnapshot(snapshotId, Optional.empty());
        }

        private static ChangeProposal change(ProjectSpecificationId projectId, String title, Provenance provenance) {
            return new ChangeProposal(
                    ChangeId.generate(), projectId, Optional.empty(), title, title + " intent",
                    List.of(), List.of(), List.of(), provenance);
        }

        private static Constraint known(ChangeId changeId, Provenance provenance) {
            return new Constraint(
                    ConstraintId.generate(), changeId, "Known", ConstraintApplicability.APPLICABLE,
                    ConstraintSeverity.WARNING, ConstraintSatisfaction.UNKNOWN,
                    ConstraintBlockingPolicy.nonBlocking(), List.of(), provenance);
        }

        private static TraceabilityLink affects(ChangeId changeId, RequirementId requirementId, EvidenceId evidenceId) {
            return new TraceabilityLink(
                    TraceabilityLinkId.generate(),
                    new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, changeId.value()),
                    TraceabilityRelationType.AFFECTS,
                    new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirementId.value()),
                    TraceabilityLinkOrigin.DERIVED,
                    TraceabilityResolutionState.RESOLVED,
                    Optional.empty(),
                    Set.of(evidenceId),
                    T0);
        }
    }
}
