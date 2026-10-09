package com.morpheus.application.ingestion;

import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingPolicy;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The normalized graph refuses every reference it cannot resolve, each by its own message.
 *
 * <p>The evidence checks all answer {@code provenance references unknown evidence}, so a refusal test that asserted
 * only the exception type would pass on any other check of the graph. Each case below makes exactly one item invalid
 * and asserts the message, which is what tells one check from another.</p>
 */
class NormalizedProjectContentTest {
    private static final String UNKNOWN_EVIDENCE = "provenance references unknown evidence: ";

    @Test
    void acceptsCoherentProjectSpecificationRequirementAndEvidenceGraph() {
        Fixture fixture = fixture();

        assertDoesNotThrow(() -> new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(fixture.requirement),
                List.of(),
                List.of(fixture.evidence),
                List.of()));
    }

    @Test
    void rejectsRequirementReferencingUnknownSpecification() {
        Fixture fixture = fixture();
        Requirement invalid = new Requirement(
                fixture.requirement.id(),
                SpecificationId.generate(),
                fixture.requirement.key(),
                fixture.requirement.title(),
                fixture.requirement.statement(),
                fixture.requirement.provenance());

        assertRefused("requirement references unknown specification: " + invalid.id(), () -> new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(invalid),
                List.of(),
                List.of(fixture.evidence),
                List.of()));
    }

    @Test
    void rejectsProvenanceReferencingUnknownEvidence() {
        Fixture fixture = fixture();
        Provenance invalidProvenance = unknownEvidence(fixture);
        Requirement invalid = new Requirement(
                fixture.requirement.id(),
                fixture.requirement.specificationId(),
                fixture.requirement.key(),
                fixture.requirement.title(),
                fixture.requirement.statement(),
                invalidProvenance);

        assertRefused(UNKNOWN_EVIDENCE + invalidProvenance.evidenceId(), () -> new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(invalid),
                List.of(),
                List.of(fixture.evidence),
                List.of()));
    }

    @Test
    void acceptsConstraintReferencingKnownChange() {
        Fixture fixture = fixture();
        ChangeProposal change = change(fixture);
        Constraint constraint = new Constraint(
                ConstraintId.generate(),
                change.id(),
                "No persistence without explicit opt-in.",
                fixture.provenance);

        assertDoesNotThrow(() -> new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(fixture.requirement),
                List.of(),
                List.of(change),
                List.of(constraint),
                List.of(),
                List.of(),
                List.of(fixture.evidence),
                List.of()));
    }

    @Test
    void rejectsConstraintReferencingUnknownChange() {
        Fixture fixture = fixture();
        ChangeProposal change = change(fixture);
        Constraint invalid = new Constraint(
                ConstraintId.generate(),
                ChangeId.generate(),
                "No persistence without explicit opt-in.",
                fixture.provenance);

        assertRefused("constraint references unknown change: " + invalid.id(), () -> new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(fixture.requirement),
                List.of(),
                List.of(change),
                List.of(invalid),
                List.of(),
                List.of(),
                List.of(fixture.evidence),
                List.of()));
    }

    @Test
    void acceptsModifiedDeltaSharingLogicalRequirementIdentityWithCurrentBaseline() {
        Fixture fixture = fixture();
        ChangeProposal change = change(fixture);
        RequirementDelta delta = delta(fixture, change.id(), fixture.requirement.id());

        assertDoesNotThrow(() -> new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(fixture.requirement),
                List.of(),
                List.of(change),
                List.of(delta),
                List.of(),
                List.of(),
                List.of(),
                List.of(fixture.evidence),
                List.of()));
    }

    @Test
    void rejectsRequirementDeltaReferencingUnknownChange() {
        Fixture fixture = fixture();
        ChangeProposal change = change(fixture);
        RequirementDelta delta = delta(fixture, ChangeId.generate(), fixture.requirement.id());

        assertRefused("requirement delta references unknown change: " + delta.id(), () -> new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(fixture.requirement),
                List.of(),
                List.of(change),
                List.of(delta),
                List.of(),
                List.of(),
                List.of(),
                List.of(fixture.evidence),
                List.of()));
    }

    @Test
    void acceptsAcceptanceCriterionReferencingKnownRequirementChangeAndEvidence() {
        Fixture fixture = fixture();
        ChangeProposal change = change(fixture);
        Evidence verificationEvidence = new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("tests/session-expiration.txt"),
                Optional.empty(),
                Optional.empty());
        AcceptanceCriterion criterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.of(fixture.requirement.id()),
                Optional.of(change.id()),
                "Inactive sessions expire",
                "An inactive session is rejected after the configured timeout",
                VerificationStatus.VERIFIED,
                List.of(verificationEvidence.id()),
                fixture.provenance);

        assertDoesNotThrow(() -> new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(fixture.requirement),
                List.of(),
                List.of(change),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(criterion),
                List.of(fixture.evidence, verificationEvidence),
                List.of()));
    }

    @Test
    void rejectsAcceptanceCriterionReferencingUnknownRequirement() {
        Fixture fixture = fixture();
        AcceptanceCriterion criterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.of(RequirementId.generate()),
                Optional.empty(),
                "Unknown owner",
                "The owner must exist in the normalized graph",
                VerificationStatus.UNKNOWN,
                List.of(),
                fixture.provenance);

        assertRefused("acceptance criterion references unknown requirement: " + criterion.id(),
                () -> new Graph(fixture).criteria(criterion).build());
    }

    @Test
    void rejectsAcceptanceCriterionReferencingUnknownVerificationEvidence() {
        Fixture fixture = fixture();
        EvidenceId unknown = EvidenceId.generate();
        AcceptanceCriterion criterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.of(fixture.requirement.id()),
                Optional.empty(),
                "Verified criterion",
                "Verification evidence must belong to the same normalized graph",
                VerificationStatus.VERIFIED,
                List.of(unknown),
                fixture.provenance);

        assertRefused(UNKNOWN_EVIDENCE + unknown, () -> new Graph(fixture).criteria(criterion).build());
    }

    @Test
    void rejectsScenarioReferencingUnknownRequirement() {
        Fixture fixture = fixture();
        Scenario scenario = scenario(Optional.of(RequirementId.generate()), fixture.provenance);

        assertRefused("scenario references unknown requirement: " + scenario.id(),
                () -> new Graph(fixture).scenarios(scenario).build());
    }

    @Test
    void rejectsDesignDecisionReferencingUnknownChange() {
        Fixture fixture = fixture();
        DesignDecision decision = new DesignDecision(
                DesignDecisionId.generate(), ChangeId.generate(), "Title", "Decision", fixture.provenance);

        assertRefused("design decision references unknown change: " + decision.id(),
                () -> new Graph(fixture).changes(change(fixture)).decisions(decision).build());
    }

    @Test
    void rejectsTaskReferencingUnknownChange() {
        Fixture fixture = fixture();
        ImplementationTask task = new ImplementationTask(
                TaskId.generate(), ChangeId.generate(), Optional.empty(), "Task", false, fixture.provenance);

        assertRefused("task references unknown change: " + task.id(),
                () -> new Graph(fixture).changes(change(fixture)).tasks(task).build());
    }

    @Test
    void rejectsAcceptanceCriterionReferencingUnknownChange() {
        Fixture fixture = fixture();
        AcceptanceCriterion criterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(), Optional.empty(), Optional.of(ChangeId.generate()), "Criterion",
                "Condition", VerificationStatus.UNKNOWN, List.of(), fixture.provenance);

        assertRefused("acceptance criterion references unknown change: " + criterion.id(),
                () -> new Graph(fixture).changes(change(fixture)).criteria(criterion).build());
    }

    @Test
    void rejectsSpecificationProvenanceReferencingUnknownEvidence() {
        Fixture fixture = fixture();
        Provenance invalid = unknownEvidence(fixture);
        Specification specification = new Specification(
                fixture.specification.id(), fixture.project.id(), fixture.specification.key(),
                fixture.specification.title(), fixture.specification.description(), invalid);

        assertRefused(UNKNOWN_EVIDENCE + invalid.evidenceId(),
                () -> new Graph(fixture).specifications(specification).build());
    }

    @Test
    void rejectsScenarioProvenanceReferencingUnknownEvidence() {
        Fixture fixture = fixture();
        Provenance invalid = unknownEvidence(fixture);

        assertRefused(UNKNOWN_EVIDENCE + invalid.evidenceId(),
                () -> new Graph(fixture).scenarios(scenario(Optional.empty(), invalid)).build());
    }

    @Test
    void rejectsChangeProvenanceReferencingUnknownEvidence() {
        Fixture fixture = fixture();
        Provenance invalid = unknownEvidence(fixture);
        ChangeProposal change = change(fixture);
        ChangeProposal invalidChange = new ChangeProposal(
                change.id(), change.projectId(), change.key(), change.title(), change.intent(), change.scope(),
                change.outOfScope(), change.risks(), invalid);

        assertRefused(UNKNOWN_EVIDENCE + invalid.evidenceId(), () -> new Graph(fixture).changes(invalidChange).build());
    }

    @Test
    void rejectsRequirementDeltaProvenanceReferencingUnknownEvidence() {
        Fixture fixture = fixture();
        Provenance invalid = unknownEvidence(fixture);
        ChangeProposal change = change(fixture);
        RequirementDelta delta = delta(fixture, change.id(), fixture.requirement.id(), List.of(), invalid);

        assertRefused(UNKNOWN_EVIDENCE + invalid.evidenceId(),
                () -> new Graph(fixture).changes(change).deltas(delta).build());
    }

    @Test
    void rejectsRequirementDeltaScenarioReferencingUnknownEvidence() {
        Fixture fixture = fixture();
        Provenance invalid = unknownEvidence(fixture);
        ChangeProposal change = change(fixture);
        RequirementDelta delta = delta(fixture, change.id(), fixture.requirement.id(),
                List.of(scenario(Optional.of(fixture.requirement.id()), invalid)), fixture.provenance);

        assertRefused(UNKNOWN_EVIDENCE + invalid.evidenceId(),
                () -> new Graph(fixture).changes(change).deltas(delta).build());
    }

    @Test
    void rejectsConstraintProvenanceReferencingUnknownEvidence() {
        Fixture fixture = fixture();
        Provenance invalid = unknownEvidence(fixture);
        ChangeProposal change = change(fixture);
        Constraint constraint = new Constraint(ConstraintId.generate(), change.id(), "Constraint", invalid);

        assertRefused(UNKNOWN_EVIDENCE + invalid.evidenceId(),
                () -> new Graph(fixture).changes(change).constraints(constraint).build());
    }

    @Test
    void rejectsConstraintSupportingEvidenceThatIsUnknown() {
        Fixture fixture = fixture();
        EvidenceId unknown = EvidenceId.generate();
        ChangeProposal change = change(fixture);
        Constraint constraint = new Constraint(
                ConstraintId.generate(), change.id(), "Constraint", ConstraintApplicability.UNKNOWN,
                ConstraintSeverity.UNKNOWN, ConstraintSatisfaction.UNKNOWN, ConstraintBlockingPolicy.unknown(),
                List.of(unknown), fixture.provenance);

        assertRefused(UNKNOWN_EVIDENCE + unknown,
                () -> new Graph(fixture).changes(change).constraints(constraint).build());
    }

    @Test
    void rejectsDesignDecisionProvenanceReferencingUnknownEvidence() {
        Fixture fixture = fixture();
        Provenance invalid = unknownEvidence(fixture);
        ChangeProposal change = change(fixture);
        DesignDecision decision = new DesignDecision(
                DesignDecisionId.generate(), change.id(), "Title", "Decision", invalid);

        assertRefused(UNKNOWN_EVIDENCE + invalid.evidenceId(),
                () -> new Graph(fixture).changes(change).decisions(decision).build());
    }

    @Test
    void rejectsTaskProvenanceReferencingUnknownEvidence() {
        Fixture fixture = fixture();
        Provenance invalid = unknownEvidence(fixture);
        ChangeProposal change = change(fixture);
        ImplementationTask task = new ImplementationTask(
                TaskId.generate(), change.id(), Optional.empty(), "Task", false, invalid);

        assertRefused(UNKNOWN_EVIDENCE + invalid.evidenceId(),
                () -> new Graph(fixture).changes(change).tasks(task).build());
    }

    @Test
    void rejectsAcceptanceCriterionProvenanceReferencingUnknownEvidence() {
        Fixture fixture = fixture();
        Provenance invalid = unknownEvidence(fixture);
        AcceptanceCriterion criterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(), Optional.of(fixture.requirement.id()), Optional.empty(), "Criterion",
                "Condition", VerificationStatus.UNKNOWN, List.of(), invalid);

        assertRefused(UNKNOWN_EVIDENCE + invalid.evidenceId(), () -> new Graph(fixture).criteria(criterion).build());
    }

    private static void assertRefused(String message, Executable construction) {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, construction);
        assertEquals(message, refusal.getMessage());
    }

    private static Provenance unknownEvidence(Fixture fixture) {
        return new Provenance(
                fixture.provenance.providerId(),
                fixture.provenance.providerVersion(),
                fixture.provenance.source(),
                fixture.provenance.externalId(),
                fixture.provenance.sourceRevision(),
                EvidenceId.generate());
    }

    private static Scenario scenario(Optional<RequirementId> requirementId, Provenance provenance) {
        return new Scenario(ScenarioId.generate(), requirementId, "Scenario", List.of(), "act", "outcome", provenance);
    }

    private RequirementDelta delta(Fixture fixture, ChangeId changeId, RequirementId requirementId) {
        return delta(fixture, changeId, requirementId, List.of(), fixture.provenance);
    }

    private RequirementDelta delta(
            Fixture fixture,
            ChangeId changeId,
            RequirementId requirementId,
            List<Scenario> scenarios,
            Provenance provenance) {
        return new RequirementDelta(
                RequirementDeltaId.generate(),
                changeId,
                RequirementDeltaKind.MODIFIED,
                fixture.specification.key(),
                requirementId,
                fixture.requirement.key(),
                fixture.requirement.title(),
                Optional.of("The system SHALL preserve remember-me sessions when explicitly requested."),
                scenarios,
                provenance);
    }

    private ChangeProposal change(Fixture fixture) {
        return new ChangeProposal(
                ChangeId.generate(),
                fixture.project.id(),
                Optional.of("add-remember-me"),
                "Add remember-me sessions",
                "Allow explicit persistent authentication.",
                List.of("Add an explicit remember-me option."),
                List.of(),
                List.of(),
                fixture.provenance);
    }

    private Fixture fixture() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceLocator source = SourceLocator.file("openspec/specs/auth-session/spec.md");
        Evidence evidence = new Evidence(EvidenceId.generate(), source, Optional.empty(), Optional.empty());
        Provenance provenance = new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1.0"),
                source,
                Optional.of("external"),
                Optional.empty(),
                evidence.id());
        ProjectSpecification project = new ProjectSpecification(
                projectId,
                "test-project",
                SourceLocator.file("workspace"));
        Specification specification = new Specification(
                SpecificationId.generate(),
                projectId,
                "auth-session",
                "Authentication Session Specification",
                Optional.empty(),
                provenance);
        Requirement requirement = new Requirement(
                RequirementId.generate(),
                specification.id(),
                Optional.of("auth-session/session-expiration"),
                "Session expiration",
                "The system SHALL expire an inactive session.",
                provenance);
        return new Fixture(project, specification, requirement, evidence, provenance);
    }

    private record Fixture(
            ProjectSpecification project,
            Specification specification,
            Requirement requirement,
            Evidence evidence,
            Provenance provenance) {
    }

    /** The fixture's valid graph, with the lists a case replaces. */
    private static final class Graph {
        private final Fixture fixture;
        private List<Specification> specifications;
        private List<Scenario> scenarios = List.of();
        private List<ChangeProposal> changes = List.of();
        private List<RequirementDelta> deltas = List.of();
        private List<Constraint> constraints = List.of();
        private List<DesignDecision> decisions = List.of();
        private List<ImplementationTask> tasks = List.of();
        private List<AcceptanceCriterion> criteria = List.of();

        private Graph(Fixture fixture) {
            this.fixture = fixture;
            this.specifications = List.of(fixture.specification);
        }

        private Graph specifications(Specification... values) { specifications = List.of(values); return this; }
        private Graph scenarios(Scenario... values) { scenarios = List.of(values); return this; }
        private Graph changes(ChangeProposal... values) { changes = List.of(values); return this; }
        private Graph deltas(RequirementDelta... values) { deltas = List.of(values); return this; }
        private Graph constraints(Constraint... values) { constraints = List.of(values); return this; }
        private Graph decisions(DesignDecision... values) { decisions = List.of(values); return this; }
        private Graph tasks(ImplementationTask... values) { tasks = List.of(values); return this; }
        private Graph criteria(AcceptanceCriterion... values) { criteria = List.of(values); return this; }

        private NormalizedProjectContent build() {
            return new NormalizedProjectContent(
                    fixture.project, specifications, List.of(fixture.requirement), scenarios, changes, deltas,
                    constraints, decisions, tasks, criteria, List.of(fixture.evidence), List.of());
        }
    }
}
