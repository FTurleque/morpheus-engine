package com.morpheus.application.composition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
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
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A composition is a union, and it says so.
 *
 * <p>The conflict said a provider was "selected" while the published content held both providers' entities under
 * different provider-scoped identities; and when the providers agreed on a value, nothing was emitted at all, so a
 * duplication that changes every denominator downstream was completely silent. Only three of the eight published
 * entity types were observed. These tests pin the union as the model, agreement as an explicit {@code IDENTICAL},
 * and every published type as observed.</p>
 */
class MultiProviderCompositionDuplicationTest {
    private static final ProviderId HIGH = new ProviderId("high");
    private static final ProviderId LOW = new ProviderId("low");
    private static final ProjectSpecificationId PROJECT = ProjectSpecificationId.generate();

    private final MultiProviderCompositionService service = new MultiProviderCompositionService();

    @Test
    void agreementOnAValueIsAnExplicitIdenticalAndNotSilence() {
        var result = compose(content(HIGH, "Statement", "Session", "Scenario", "Task"), content(LOW, "Statement", "Session", "Scenario", "Task"));

        List<CompositionConflict> requirement = of(result, CompositionEntityType.REQUIREMENT);
        assertFalse(requirement.isEmpty(), "two providers publishing the same key are a duplication even when they agree");
        assertTrue(requirement.stream().allMatch(conflict -> conflict.resolution() == CompositionResolution.IDENTICAL));
        assertTrue(requirement.stream().allMatch(conflict -> conflict.selectedProviderId().isEmpty()));
    }

    @Test
    void divergenceRecordsAPrecedenceThatThePublishedContentDoesNotApply() {
        var result = compose(content(HIGH, "High statement", "Session", "Scenario", "Task"), content(LOW, "Low statement", "Session", "Scenario", "Task"));

        CompositionConflict statement = of(result, CompositionEntityType.REQUIREMENT).stream()
                .filter(conflict -> conflict.field().equals("statement")).findFirst().orElseThrow();

        assertEquals(CompositionResolution.PRECEDENCE_RECORDED, statement.resolution());
        assertEquals(Optional.of(HIGH), statement.selectedProviderId());
        assertFalse(statement.reason().toLowerCase().contains("selected"), statement.reason());
        assertEquals(2, result.content().requirements().size(),
                "both observations are published: the precedence is recorded, not applied");
    }

    @Test
    void everyPublishedEntityTypeIsObserved() {
        var result = compose(content(HIGH, "Statement", "Session", "Scenario", "Task"), content(LOW, "Statement", "Session", "Scenario", "Task"));

        for (CompositionEntityType type : List.of(
                CompositionEntityType.PROJECT, CompositionEntityType.SPECIFICATION, CompositionEntityType.REQUIREMENT,
                CompositionEntityType.SCENARIO, CompositionEntityType.CONSTRAINT, CompositionEntityType.DESIGN_DECISION,
                CompositionEntityType.TASK, CompositionEntityType.ACCEPTANCE_CRITERION)) {
            assertFalse(of(result, type).isEmpty(), type + " duplicates must be reported");
        }
    }

    @Test
    void aScenarioThatDivergesBetweenProvidersIsAConflict() {
        var result = compose(content(HIGH, "Statement", "Session", "High scenario", "Task"), content(LOW, "Statement", "Session", "Low scenario", "Task"));

        assertTrue(of(result, CompositionEntityType.SCENARIO).stream().anyMatch(conflict ->
                conflict.field().equals("title") && conflict.resolution() == CompositionResolution.PRECEDENCE_RECORDED));
    }

    @Test
    void aProjectDisplayNameThatDivergesIsAConflictAndTheRootIsNeverPublishedAsAPath() {
        var high = content(HIGH, "Statement", "High name", "Scenario", "Task");
        var low = content(LOW, "Statement", "Low name", "Scenario", "Task");

        var result = compose(high, low);

        assertTrue(of(result, CompositionEntityType.PROJECT).stream().anyMatch(conflict ->
                conflict.field().equals("displayName") && conflict.resolution() == CompositionResolution.PRECEDENCE_RECORDED));
        of(result, CompositionEntityType.PROJECT).stream()
                .filter(conflict -> conflict.field().equals("rootLocator"))
                .flatMap(conflict -> conflict.candidates().stream())
                .forEach(candidate -> assertTrue(candidate.value().startsWith("sha256:") && !candidate.value().contains("workspace"),
                        "a conflict is published remotely: the root locator must not appear as a path: " + candidate.value()));
    }

    @Test
    void aSingleProviderCompositionKeepsItsContentAndReportsNothing() {
        var only = content(HIGH, "Statement", "Session", "Scenario", "Task");

        var result = service.compose(List.of(contribution(HIGH, 100, only)));

        assertEquals(List.of(), result.conflicts());
        assertEquals(only.requirements(), result.content().requirements());
        assertEquals(only.scenarios(), result.content().scenarios());
        assertEquals(only.tasks(), result.content().tasks());
        assertEquals(only.acceptanceCriteria(), result.content().acceptanceCriteria());
    }

    private MultiProviderCompositionResult compose(NormalizedProjectContent high, NormalizedProjectContent low) {
        return service.compose(List.of(contribution(HIGH, 100, high), contribution(LOW, 50, low)));
    }

    private static List<CompositionConflict> of(MultiProviderCompositionResult result, CompositionEntityType type) {
        return result.conflicts().stream().filter(conflict -> conflict.entityType() == type).toList();
    }

    private static ProviderContribution contribution(ProviderId provider, int priority, NormalizedProjectContent content) {
        return new ProviderContribution(provider, priority, true,
                new ProviderReadResult(provider, Optional.of(content), List.of(), List.of()));
    }

    private static NormalizedProjectContent content(
            ProviderId provider, String statement, String projectName, String scenarioTitle, String taskTitle) {
        Evidence evidence = new Evidence(
                EvidenceId.generate(), SourceLocator.file("specs/" + provider.value() + ".md"), Optional.empty(),
                Optional.of("sha256:" + provider.value()));
        Provenance provenance = provenance(provider, evidence, "K");
        SpecificationId specificationId = SpecificationId.generate();
        Specification specification = new Specification(
                specificationId, PROJECT, "spec-1", "Specification", Optional.empty(), provenance);
        RequirementId requirementId = RequirementId.generate();
        Requirement requirement = new Requirement(
                requirementId, specificationId, Optional.of("R-1"), "Requirement", statement, provenance);
        Scenario scenario = new Scenario(
                ScenarioId.generate(), Optional.of(requirementId), scenarioTitle, List.of(), "act", "outcome",
                provenance(provider, evidence, "SC-1"));
        ChangeId changeId = ChangeId.generate();
        Constraint constraint = new Constraint(
                ConstraintId.generate(), changeId, "Constraint", provenance(provider, evidence, "CON-1"));
        DesignDecision decision = new DesignDecision(
                DesignDecisionId.generate(), changeId, "Decision", "Because", provenance(provider, evidence, "DEC-1"));
        ImplementationTask task = new ImplementationTask(
                TaskId.generate(), changeId, Optional.of("T-1"), taskTitle, false, provenance(provider, evidence, "T-1"));
        AcceptanceCriterion criterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(), Optional.of(requirementId), Optional.empty(), "Criterion", "Condition",
                VerificationStatus.NOT_VERIFIED, List.of(), provenance(provider, evidence, "AC-1"));
        return new NormalizedProjectContent(
                new ProjectSpecification(PROJECT, projectName, SourceLocator.file("/srv/workspace/" + provider.value())),
                List.of(specification), List.of(requirement), List.of(scenario), List.of(), List.of(),
                List.of(constraint), List.of(decision), List.of(task), List.of(criterion), List.of(evidence), List.of());
    }

    private static Provenance provenance(ProviderId provider, Evidence evidence, String externalId) {
        return new Provenance(
                provider, Optional.of("1"), SourceLocator.file("specs/" + provider.value() + ".md"),
                Optional.of(externalId), Optional.of("rev"), evidence.id());
    }
}
