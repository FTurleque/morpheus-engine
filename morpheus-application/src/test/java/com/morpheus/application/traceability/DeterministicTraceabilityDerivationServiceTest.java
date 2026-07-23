package com.morpheus.application.traceability;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
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
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicTraceabilityDerivationServiceTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-23T11:00:00Z");

    private final DeterministicTraceabilityDerivationService service =
            new DeterministicTraceabilityDerivationService();

    @Test
    void derivesOnlySupportedStructuralRelationsWithFactEvidence() {
        Fixture fixture = fixture(true);
        Map<TraceabilityDerivationKey, EvidenceId> expectedEvidence = expectedFacts(fixture);
        Map<TraceabilityDerivationKey, TraceabilityLinkId> identities = identities(expectedEvidence.keySet());

        List<TraceabilityLink> links = service.derive(
                fixture.content,
                key -> Optional.ofNullable(identities.get(key)),
                OBSERVED_AT);

        assertEquals(expectedEvidence.size(), links.size());
        expectedEvidence.forEach((key, evidenceId) -> {
            TraceabilityLink link = links.stream()
                    .filter(candidate -> candidate.id().equals(identities.get(key)))
                    .findFirst()
                    .orElseThrow();
            assertEquals(key.source(), link.source());
            assertEquals(key.relationType(), link.relationType());
            assertEquals(key.target(), link.target());
            assertEquals(TraceabilityLinkOrigin.DERIVED, link.origin());
            assertEquals(TraceabilityResolutionState.RESOLVED, link.resolution());
            assertTrue(link.confidence().isEmpty());
            assertEquals(Set.of(evidenceId), link.evidenceIds());
            assertEquals(OBSERVED_AT, link.observedAt());
        });
    }

    @Test
    void scenarioWithoutRequirementAndTaskDoNotInventTraceability() {
        Fixture fixture = fixture(false);
        Scenario unattached = new Scenario(
                ScenarioId.generate(),
                Optional.empty(),
                fixture.requirement.title(),
                List.of(),
                "Implement " + fixture.requirement.title(),
                fixture.requirement.statement(),
                fixture.scenario.provenance());
        ImplementationTask misleadingTask = new ImplementationTask(
                TaskId.generate(),
                fixture.change.id(),
                Optional.of("task-same-title"),
                fixture.requirement.title(),
                false,
                fixture.task.provenance());

        NormalizedProjectContent content = new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(fixture.requirement),
                List.of(unattached),
                List.of(fixture.change),
                List.of(),
                List.of(),
                List.of(),
                List.of(misleadingTask),
                fixture.evidence,
                List.of());
        TraceabilityDerivationKey requirementFact = requirementToSpecification(fixture.requirement);
        TraceabilityLinkId id = TraceabilityLinkId.generate();

        List<TraceabilityLink> links = service.derive(
                content,
                key -> key.equals(requirementFact) ? Optional.of(id) : Optional.empty(),
                OBSERVED_AT);

        assertEquals(1, links.size());
        assertEquals(TraceabilityRelationType.DERIVES_FROM, links.getFirst().relationType());
    }

    @Test
    void missingExplicitLinkIdentityFailsInsteadOfAllocatingOne() {
        Fixture fixture = fixture(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.derive(fixture.content, key -> Optional.empty(), OBSERVED_AT));
    }

    @Test
    void sameLinkIdentityCannotRepresentDifferentDerivationFacts() {
        Fixture fixture = fixture(true);
        TraceabilityLinkId duplicate = TraceabilityLinkId.generate();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.derive(fixture.content, key -> Optional.of(duplicate), OBSERVED_AT));
    }

    @Test
    void inputListOrderDoesNotChangeDerivedLinkOrder() {
        Fixture fixture = fixture(false);
        RequirementDelta deltaTwo = delta(
                fixture,
                RequirementDeltaId.generate(),
                fixture.delta.provenance(),
                List.of());
        Constraint constraintTwo = new Constraint(
                ConstraintId.generate(),
                fixture.change.id(),
                "Second structural constraint.",
                fixture.constraint.provenance());

        NormalizedProjectContent first = content(
                fixture,
                List.of(fixture.delta, deltaTwo),
                List.of(fixture.constraint, constraintTwo),
                List.of(fixture.decision));
        NormalizedProjectContent reversed = content(
                fixture,
                List.of(deltaTwo, fixture.delta),
                List.of(constraintTwo, fixture.constraint),
                List.of(fixture.decision));

        Map<TraceabilityDerivationKey, TraceabilityLinkId> identities = identities(keysFor(first));
        TraceabilityLinkIdentityResolver resolver = key -> Optional.ofNullable(identities.get(key));

        assertEquals(
                service.derive(first, resolver, OBSERVED_AT),
                service.derive(reversed, resolver, OBSERVED_AT));
    }

    @Test
    void distinctRequirementDeltaFactsToSameRequirementRemainDistinctObservations() {
        Fixture fixture = fixture(false);
        Evidence secondEvidence = evidence("delta-two");
        Provenance secondProvenance = provenance(secondEvidence, "DELTA-2");
        RequirementDelta secondDelta = delta(
                fixture,
                RequirementDeltaId.generate(),
                secondProvenance,
                List.of());
        List<Evidence> evidence = new ArrayList<>(fixture.evidence);
        evidence.add(secondEvidence);

        NormalizedProjectContent content = new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(fixture.requirement),
                List.of(),
                List.of(fixture.change),
                List.of(fixture.delta, secondDelta),
                List.of(),
                List.of(),
                List.of(),
                evidence,
                List.of());
        Map<TraceabilityDerivationKey, TraceabilityLinkId> identities = identities(keysFor(content));

        List<TraceabilityLink> affects = service.derive(
                        content,
                        key -> Optional.ofNullable(identities.get(key)),
                        OBSERVED_AT)
                .stream()
                .filter(link -> link.relationType() == TraceabilityRelationType.AFFECTS)
                .toList();

        assertEquals(2, affects.size());
        assertEquals(2, affects.stream().map(TraceabilityLink::id).distinct().count());
        assertEquals(2, affects.stream().flatMap(link -> link.evidenceIds().stream()).distinct().count());
    }

    @Test
    void exactDuplicateFactKeyCombinesEvidenceWithoutSimilarityMatching() {
        Fixture fixture = fixture(false);
        Evidence secondEvidence = evidence("same-scenario-second-proof");
        Scenario duplicateObservation = new Scenario(
                fixture.scenario.id(),
                fixture.scenario.requirementId(),
                fixture.scenario.title(),
                fixture.scenario.preconditions(),
                fixture.scenario.action(),
                fixture.scenario.expectedOutcome(),
                provenance(secondEvidence, "SCENARIO-SECOND-PROOF"));
        List<Evidence> evidence = new ArrayList<>(fixture.evidence);
        evidence.add(secondEvidence);

        NormalizedProjectContent content = new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(fixture.requirement),
                List.of(fixture.scenario, duplicateObservation),
                List.of(fixture.change),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                evidence,
                List.of());
        Map<TraceabilityDerivationKey, TraceabilityLinkId> identities = identities(keysFor(content));

        List<TraceabilityLink> refines = service.derive(
                        content,
                        key -> Optional.ofNullable(identities.get(key)),
                        OBSERVED_AT)
                .stream()
                .filter(link -> link.relationType() == TraceabilityRelationType.REFINES)
                .toList();

        assertEquals(1, refines.size());
        assertEquals(
                Set.of(fixture.scenario.provenance().evidenceId(), secondEvidence.id()),
                refines.getFirst().evidenceIds());
    }

    private Fixture fixture(boolean includeDeltaScenario) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ProjectSpecification project = new ProjectSpecification(
                projectId,
                "traceability-project",
                SourceLocator.file("workspace"));

        Evidence specificationEvidence = evidence("specification");
        Evidence requirementEvidence = evidence("requirement");
        Evidence scenarioEvidence = evidence("scenario");
        Evidence deltaScenarioEvidence = evidence("delta-scenario");
        Evidence changeEvidence = evidence("change");
        Evidence constraintEvidence = evidence("constraint");
        Evidence decisionEvidence = evidence("decision");
        Evidence deltaEvidence = evidence("delta");
        Evidence taskEvidence = evidence("task");

        Specification specification = new Specification(
                SpecificationId.generate(),
                projectId,
                "billing",
                "Billing",
                Optional.empty(),
                provenance(specificationEvidence, "SPEC-BILLING"));
        Requirement requirement = new Requirement(
                RequirementId.generate(),
                specification.id(),
                Optional.of("invoice-retention"),
                "Invoice retention",
                "Invoices SHALL be retained for the configured period.",
                provenance(requirementEvidence, "REQ-RETENTION"));
        Scenario scenario = new Scenario(
                ScenarioId.generate(),
                Optional.of(requirement.id()),
                "Retain an invoice",
                List.of("An invoice exists"),
                "The retention job runs",
                "The invoice remains available",
                provenance(scenarioEvidence, "SCENARIO-RETENTION"));
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(),
                projectId,
                Optional.of("extend-retention"),
                "Extend retention",
                "Support a longer retention period.",
                List.of("Billing retention"),
                List.of(),
                List.of(),
                provenance(changeEvidence, "CHANGE-RETENTION"));
        Constraint constraint = new Constraint(
                ConstraintId.generate(),
                change.id(),
                "Retention remains configurable.",
                provenance(constraintEvidence, "CONSTRAINT-CONFIG"));
        DesignDecision decision = new DesignDecision(
                DesignDecisionId.generate(),
                change.id(),
                "Keep retention policy explicit",
                "Represent the period as explicit configuration.",
                provenance(decisionEvidence, "DECISION-CONFIG"));
        Scenario deltaScenario = new Scenario(
                ScenarioId.generate(),
                Optional.of(requirement.id()),
                "Retain for extended period",
                List.of("Extended retention is configured"),
                "The retention job runs",
                "The invoice follows the extended period",
                provenance(deltaScenarioEvidence, "SCENARIO-DELTA"));
        RequirementDelta delta = delta(
                new FixtureShell(project, specification, requirement, change),
                RequirementDeltaId.generate(),
                provenance(deltaEvidence, "DELTA-RETENTION"),
                includeDeltaScenario ? List.of(deltaScenario) : List.of());
        ImplementationTask task = new ImplementationTask(
                TaskId.generate(),
                change.id(),
                Optional.of("implement-retention"),
                "Implement invoice retention",
                false,
                provenance(taskEvidence, "TASK-RETENTION"));

        List<Evidence> evidence = List.of(
                specificationEvidence,
                requirementEvidence,
                scenarioEvidence,
                deltaScenarioEvidence,
                changeEvidence,
                constraintEvidence,
                decisionEvidence,
                deltaEvidence,
                taskEvidence);

        NormalizedProjectContent content = new NormalizedProjectContent(
                project,
                List.of(specification),
                List.of(requirement),
                List.of(scenario),
                List.of(change),
                List.of(delta),
                List.of(constraint),
                List.of(decision),
                List.of(task),
                evidence,
                List.of());

        return new Fixture(
                project,
                specification,
                requirement,
                scenario,
                deltaScenario,
                change,
                constraint,
                decision,
                delta,
                task,
                evidence,
                content);
    }

    private RequirementDelta delta(
            Fixture fixture,
            RequirementDeltaId id,
            Provenance provenance,
            List<Scenario> scenarios) {
        return delta(new FixtureShell(fixture.project, fixture.specification, fixture.requirement, fixture.change), id, provenance, scenarios);
    }

    private RequirementDelta delta(
            FixtureShell fixture,
            RequirementDeltaId id,
            Provenance provenance,
            List<Scenario> scenarios) {
        return new RequirementDelta(
                id,
                fixture.change.id(),
                RequirementDeltaKind.MODIFIED,
                fixture.specification.key(),
                fixture.requirement.id(),
                fixture.requirement.key(),
                fixture.requirement.title(),
                Optional.of("Invoices SHALL be retained for the extended configured period."),
                scenarios,
                provenance);
    }

    private NormalizedProjectContent content(
            Fixture fixture,
            List<RequirementDelta> deltas,
            List<Constraint> constraints,
            List<DesignDecision> decisions) {
        return new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(fixture.requirement),
                List.of(fixture.scenario),
                List.of(fixture.change),
                deltas,
                constraints,
                decisions,
                List.of(fixture.task),
                fixture.evidence,
                List.of());
    }

    private Map<TraceabilityDerivationKey, EvidenceId> expectedFacts(Fixture fixture) {
        Map<TraceabilityDerivationKey, EvidenceId> facts = new LinkedHashMap<>();
        facts.put(requirementToSpecification(fixture.requirement), fixture.requirement.provenance().evidenceId());
        facts.put(scenarioToRequirement(fixture.scenario), fixture.scenario.provenance().evidenceId());
        facts.put(constraintToChange(fixture.constraint), fixture.constraint.provenance().evidenceId());
        facts.put(changeToDecision(fixture.decision), fixture.decision.provenance().evidenceId());
        facts.put(changeToRequirement(fixture.delta), fixture.delta.provenance().evidenceId());
        if (!fixture.delta.scenarios().isEmpty()) {
            facts.put(scenarioToRequirement(fixture.deltaScenario), fixture.deltaScenario.provenance().evidenceId());
        }
        return facts;
    }

    private Set<TraceabilityDerivationKey> keysFor(NormalizedProjectContent content) {
        Map<TraceabilityDerivationKey, TraceabilityLinkId> captured = new HashMap<>();
        service.derive(content, key -> Optional.of(captured.computeIfAbsent(key, ignored -> TraceabilityLinkId.generate())), OBSERVED_AT);
        return Set.copyOf(captured.keySet());
    }

    private Map<TraceabilityDerivationKey, TraceabilityLinkId> identities(Set<TraceabilityDerivationKey> keys) {
        Map<TraceabilityDerivationKey, TraceabilityLinkId> identities = new HashMap<>();
        keys.forEach(key -> identities.put(key, TraceabilityLinkId.generate()));
        return identities;
    }

    private TraceabilityDerivationKey requirementToSpecification(Requirement requirement) {
        return new TraceabilityDerivationKey(
                ref(TraceabilityEntityKind.REQUIREMENT, requirement.id().value()),
                ref(TraceabilityEntityKind.REQUIREMENT, requirement.id().value()),
                TraceabilityRelationType.DERIVES_FROM,
                ref(TraceabilityEntityKind.SPECIFICATION, requirement.specificationId().value()));
    }

    private TraceabilityDerivationKey scenarioToRequirement(Scenario scenario) {
        return new TraceabilityDerivationKey(
                ref(TraceabilityEntityKind.SCENARIO, scenario.id().value()),
                ref(TraceabilityEntityKind.SCENARIO, scenario.id().value()),
                TraceabilityRelationType.REFINES,
                ref(TraceabilityEntityKind.REQUIREMENT, scenario.requirementId().orElseThrow().value()));
    }

    private TraceabilityDerivationKey constraintToChange(Constraint constraint) {
        return new TraceabilityDerivationKey(
                ref(TraceabilityEntityKind.CONSTRAINT, constraint.id().value()),
                ref(TraceabilityEntityKind.CONSTRAINT, constraint.id().value()),
                TraceabilityRelationType.CONSTRAINS,
                ref(TraceabilityEntityKind.CHANGE, constraint.changeId().value()));
    }

    private TraceabilityDerivationKey changeToDecision(DesignDecision decision) {
        return new TraceabilityDerivationKey(
                ref(TraceabilityEntityKind.DESIGN_DECISION, decision.id().value()),
                ref(TraceabilityEntityKind.CHANGE, decision.changeId().value()),
                TraceabilityRelationType.DECIDED_BY,
                ref(TraceabilityEntityKind.DESIGN_DECISION, decision.id().value()));
    }

    private TraceabilityDerivationKey changeToRequirement(RequirementDelta delta) {
        return new TraceabilityDerivationKey(
                ref(TraceabilityEntityKind.REQUIREMENT_DELTA, delta.id().value()),
                ref(TraceabilityEntityKind.CHANGE, delta.changeId().value()),
                TraceabilityRelationType.AFFECTS,
                ref(TraceabilityEntityKind.REQUIREMENT, delta.requirementId().value()));
    }

    private TraceabilityEntityRef ref(TraceabilityEntityKind kind, com.morpheus.domain.identity.DomainIdentity identity) {
        return new TraceabilityEntityRef(kind, identity);
    }

    private Evidence evidence(String name) {
        return new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("specs/" + name + ".md"),
                Optional.empty(),
                Optional.empty());
    }

    private Provenance provenance(Evidence evidence, String externalId) {
        return new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1.0"),
                evidence.source(),
                Optional.of(externalId),
                Optional.of("revision-1"),
                evidence.id());
    }

    private record FixtureShell(
            ProjectSpecification project,
            Specification specification,
            Requirement requirement,
            ChangeProposal change) {
    }

    private record Fixture(
            ProjectSpecification project,
            Specification specification,
            Requirement requirement,
            Scenario scenario,
            Scenario deltaScenario,
            ChangeProposal change,
            Constraint constraint,
            DesignDecision decision,
            RequirementDelta delta,
            ImplementationTask task,
            List<Evidence> evidence,
            NormalizedProjectContent content) {
    }
}
