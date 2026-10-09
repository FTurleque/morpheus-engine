package com.morpheus.application.composition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    /**
     * Two providers sharing a key report one conflict per observed field, so a type with <em>a</em> conflict proves
     * nothing about the others: the exact field set is what shows each field is observed.
     */
    @Test
    void everyPublishedEntityTypeIsObservedOnEachOfItsFields() {
        var result = composeAgreeingProviders();

        Map<CompositionEntityType, Set<String>> fields = result.conflicts().stream().collect(Collectors.groupingBy(
                CompositionConflict::entityType, Collectors.mapping(CompositionConflict::field, Collectors.toSet())));

        assertEquals(Map.of(
                CompositionEntityType.PROJECT, Set.of("displayName", "rootLocator"),
                CompositionEntityType.SPECIFICATION, Set.of("title", "description"),
                CompositionEntityType.REQUIREMENT, Set.of("title", "statement", "ownerSpecification"),
                CompositionEntityType.SCENARIO, Set.of("title", "preconditions", "action", "expectedOutcome"),
                CompositionEntityType.CHANGE, Set.of("title", "intent", "scope", "outOfScope", "risks"),
                CompositionEntityType.CONSTRAINT, Set.of("statement", "applicability", "severity"),
                CompositionEntityType.DESIGN_DECISION, Set.of("title", "decision"),
                CompositionEntityType.TASK, Set.of("title", "completed"),
                CompositionEntityType.ACCEPTANCE_CRITERION, Set.of("title", "condition")), fields);
    }

    @Test
    void contributionsOfDifferentProjectsAreRefusedByName() {
        var high = content(HIGH, PROJECT, "Statement", "Session", List.of());
        var low = content(LOW, ProjectSpecificationId.generate(), "Statement", "Session", List.of());

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> compose(high, low));

        assertEquals("provider contributions belong to different projects", refusal.getMessage());
    }

    /** A secondary provider's blocking diagnostic must reach the publication guard, which reads the composed content. */
    @Test
    void secondaryDiagnosticsAreAppendedOnceAfterThePrimaryOnes() {
        Diagnostic shared = Diagnostic.warning(DiagnosticCode.UNSUPPORTED_SOURCE, "shared", Map.of());
        Diagnostic blocking = Diagnostic.error(DiagnosticCode.INVALID_SOURCE, "secondary only", Map.of());
        var high = content(HIGH, PROJECT, "Statement", "Session", List.of(shared));
        var low = content(LOW, PROJECT, "Statement", "Session", List.of(shared, blocking));

        var result = compose(high, low);

        assertEquals(List.of(shared, blocking), result.content().diagnostics());
    }

    @Test
    void aSpecificationARequirementAndAChangeSharingAKeyAreAnIdentityConflict() {
        var base = content(HIGH, "Statement", "Session", "Scenario", "Task");
        Requirement requirement = base.requirements().getFirst();
        Requirement sharingTheSpecificationKey = new Requirement(
                requirement.id(), requirement.specificationId(), Optional.of("spec-1"),
                requirement.title(), requirement.statement(), requirement.provenance());
        ChangeProposal change = base.changes().getFirst();
        ChangeProposal changeSharingIt = new ChangeProposal(
                change.id(), change.projectId(), Optional.of("spec-1"), change.title(), change.intent(),
                change.scope(), change.outOfScope(), change.risks(), change.provenance());
        var content = new NormalizedProjectContent(
                base.project(), base.specifications(), List.of(sharingTheSpecificationKey), base.scenarios(),
                List.of(changeSharingIt), base.requirementDeltas(), base.constraints(), base.designDecisions(),
                base.tasks(), base.acceptanceCriteria(), base.evidence(), base.diagnostics());

        var result = service.compose(List.of(contribution(HIGH, 100, content)));

        CompositionConflict identity = of(result, CompositionEntityType.IDENTITY).stream()
                .filter(conflict -> conflict.logicalKey().equals("spec-1")).findFirst().orElseThrow();
        assertEquals("entityType", identity.field());
        assertEquals(Set.of("SPECIFICATION", "REQUIREMENT", "CHANGE"),
                identity.candidates().stream().map(CompositionCandidate::value).collect(Collectors.toSet()));
    }

    @Test
    void conflictsAreOrderedByEntityTypeThenKeyThenField() {
        var result = composeAgreeingProviders();

        Comparator<CompositionConflict> byKeyThenField =
                Comparator.comparing(CompositionConflict::logicalKey).thenComparing(CompositionConflict::field);
        List<CompositionConflict> expected = result.conflicts().stream()
                .sorted(Comparator.comparing((CompositionConflict conflict) -> conflict.entityType().name())
                        .thenComparing(byKeyThenField))
                .toList();

        assertEquals(expected, result.conflicts());
        assertNotEquals(result.conflicts().stream().sorted(byKeyThenField).toList(), expected,
                "the fixture must tell the entity-type order apart from the key order");
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
        CompositionConflict root = of(result, CompositionEntityType.PROJECT).stream()
                .filter(conflict -> conflict.field().equals("rootLocator")).findFirst()
                .orElseThrow(() -> new AssertionError("two different roots must be a conflict, or the check below is vacuous"));
        assertEquals(CompositionResolution.PRECEDENCE_RECORDED, root.resolution());
        assertEquals(2, root.candidates().stream().map(CompositionCandidate::value).distinct().count(), root.toString());
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

    private MultiProviderCompositionResult composeAgreeingProviders() {
        return compose(content(HIGH, "Statement", "Session", "Scenario", "Task"),
                content(LOW, "Statement", "Session", "Scenario", "Task"));
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
        return content(provider, PROJECT, statement, projectName, scenarioTitle, taskTitle, List.of());
    }

    private static NormalizedProjectContent content(
            ProviderId provider, ProjectSpecificationId project, String statement, String projectName,
            List<Diagnostic> diagnostics) {
        return content(provider, project, statement, projectName, "Scenario", "Task", diagnostics);
    }

    private static NormalizedProjectContent content(
            ProviderId provider, ProjectSpecificationId project, String statement, String projectName,
            String scenarioTitle, String taskTitle, List<Diagnostic> diagnostics) {
        Evidence evidence = new Evidence(
                EvidenceId.generate(), SourceLocator.file("specs/" + provider.value() + ".md"), Optional.empty(),
                Optional.of("sha256:" + provider.value()));
        Provenance provenance = provenance(provider, evidence, "K");
        SpecificationId specificationId = SpecificationId.generate();
        Specification specification = new Specification(
                specificationId, project, "spec-1", "Specification", Optional.empty(), provenance);
        RequirementId requirementId = RequirementId.generate();
        Requirement requirement = new Requirement(
                requirementId, specificationId, Optional.of("R-1"), "Requirement", statement, provenance);
        Scenario scenario = new Scenario(
                ScenarioId.generate(), Optional.of(requirementId), scenarioTitle, List.of(), "act", "outcome",
                provenance(provider, evidence, "SC-1"));
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(), project, Optional.of("C-1"), "Change", "Intent", List.of(), List.of(), List.of(),
                provenance(provider, evidence, "C-1"));
        ChangeId changeId = change.id();
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
                new ProjectSpecification(project, projectName, SourceLocator.file("/srv/workspace/" + provider.value())),
                List.of(specification), List.of(requirement), List.of(scenario), List.of(change), List.of(),
                List.of(constraint), List.of(decision), List.of(task), List.of(criterion), List.of(evidence), diagnostics);
    }

    private static Provenance provenance(ProviderId provider, Evidence evidence, String externalId) {
        return new Provenance(
                provider, Optional.of("1"), SourceLocator.file("specs/" + provider.value() + ".md"),
                Optional.of(externalId), Optional.of("rev"), evidence.id());
    }
}
