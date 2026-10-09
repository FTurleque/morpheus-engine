package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.composition.CompositionCandidate;
import com.morpheus.application.composition.CompositionConflict;
import com.morpheus.application.composition.CompositionEntityType;
import com.morpheus.application.composition.CompositionProviderState;
import com.morpheus.application.composition.CompositionQueryService;
import com.morpheus.application.composition.CompositionResolution;
import com.morpheus.application.composition.CompositionSnapshotState;
import com.morpheus.application.policy.DefaultPolicyFactResolver;
import com.morpheus.application.policy.PolicyEvaluation;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.query.ConstraintEvaluationQueryService;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
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
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemoryCompositionStateStore;
import com.morpheus.store.memory.MemoryExternalReferenceStore;
import com.morpheus.store.memory.MemoryPortfolioStore;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A ratio is not a measurement over a population known to be duplicated.
 *
 * <p>A multi-provider composition publishes each provider's entity separately, so a key observed by two providers
 * is in the denominator twice. A coverage ratio computed on it is a number about a population that does not
 * exist; the policy fact is UNKNOWN instead. A count stays a count (how many entities are published is true), and
 * a project with no composition state, the case of every single-provider user, is measured exactly as before.</p>
 */
class DuplicatedCompositionPolicyContractTest {
    private static final Instant T0 = Instant.parse("2026-09-25T10:00:00Z");

    private final MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
    private final MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
    private final MemoryTraceabilityStore traceability = new MemoryTraceabilityStore(core);
    private final MemoryCompositionStateStore compositions = new MemoryCompositionStateStore();

    @Test
    void aProjectWithoutCompositionStateKeepsMeasuringItsRatio() {
        Seeded project = seed();

        PolicyEvaluation.Fact fact = resolve(project, PolicyRule.QualityMetric.REQUIREMENT_COVERAGE_PERCENT);

        assertEquals(PolicyEvaluation.FactState.FAIL, fact.state(), "0 of 1 requirements linked is measured, not unknown");
        assertEquals(0.0, fact.observedValue().orElseThrow());
    }

    @Test
    void aRatioOverADuplicatedRequirementPopulationIsUnknown() {
        Seeded project = seed();
        compositions.save(stateWithDuplicate(project, CompositionEntityType.REQUIREMENT));

        PolicyEvaluation.Fact fact = resolve(project, PolicyRule.QualityMetric.REQUIREMENT_COVERAGE_PERCENT);

        assertEquals(PolicyEvaluation.FactState.UNKNOWN, fact.state());
        assertTrue(fact.observedValue().isEmpty(), "an undefined ratio must not carry an observed value");
        assertTrue(fact.reason().contains("duplicated REQUIREMENT"), fact.reason());
    }

    @Test
    void aDuplicatedPopulationOfAnotherTypeDoesNotMakeThisRatioUnknown() {
        Seeded project = seed();
        compositions.save(stateWithDuplicate(project, CompositionEntityType.SCENARIO));

        PolicyEvaluation.Fact fact = resolve(project, PolicyRule.QualityMetric.REQUIREMENT_COVERAGE_PERCENT);

        assertEquals(PolicyEvaluation.FactState.FAIL, fact.state());
    }

    @Test
    void aCountOverADuplicatedPopulationIsStillACount() {
        Seeded project = seed();
        compositions.save(stateWithDuplicate(project, CompositionEntityType.REQUIREMENT));

        PolicyEvaluation.Fact fact = resolve(project, PolicyRule.QualityMetric.ORPHAN_REQUIREMENTS);

        assertEquals(1.0, fact.observedValue().orElseThrow());
    }

    private CompositionSnapshotState stateWithDuplicate(Seeded project, CompositionEntityType type) {
        ProviderId high = new ProviderId("high");
        ProviderId low = new ProviderId("low");
        CompositionConflict conflict = new CompositionConflict(
                type, "R-1", "title",
                List.of(new CompositionCandidate(high, 100, "T", "specs/a.md", "e1"),
                        new CompositionCandidate(low, 50, "T", "specs/b.md", "e2")),
                CompositionResolution.IDENTICAL, Optional.empty(), "All providers agree");
        return new CompositionSnapshotState(
                project.snapshotId(), high,
                List.of(new CompositionProviderState(high, 100, true, true, 0),
                        new CompositionProviderState(low, 50, false, true, 0)),
                List.of(conflict));
    }

    private PolicyEvaluation.Fact resolve(Seeded project, PolicyRule.QualityMetric metric) {
        PolicyRule.Comparison comparison = metric == PolicyRule.QualityMetric.ORPHAN_REQUIREMENTS
                ? PolicyRule.Comparison.LTE
                : PolicyRule.Comparison.GTE;
        double threshold = metric == PolicyRule.QualityMetric.ORPHAN_REQUIREMENTS ? 0.0 : 80.0;
        PolicyRule rule = new PolicyRule(
                PolicyIds.RuleId.generate(), "Quality threshold on " + metric, PolicyRule.Kind.QUALITY_THRESHOLD,
                PolicyRule.Severity.BLOCKER, new PolicyRule.QualityThreshold(metric, comparison, threshold));
        return resolver().resolve(new PolicyScope.Project(project.projectId()), rule);
    }

    private DefaultPolicyFactResolver resolver() {
        MemoryExternalReferenceStore references = new MemoryExternalReferenceStore(core);
        QualityReportService quality = new QualityReportService(
                core,
                new RequirementQualityService(core, core, traceability),
                new TaskQualityService(core, content, core, traceability),
                new AcceptanceQualityService(core, content),
                new ChangeCompletenessService(core, content, core, traceability),
                new DecisionReferenceQualityService(core, content, core, traceability, references));
        return new DefaultPolicyFactResolver(
                new ConstraintEvaluationQueryService(core, content),
                new ChangeTransitionEvaluationService(core, content, core, traceability),
                quality,
                new QueryExecutionService(core, core, content, new MemoryPortfolioStore()),
                new CompositionQueryService(core, compositions));
    }

    private Seeded seed() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        Evidence evidence = new Evidence(
                EvidenceId.generate(), SourceLocator.file("specs/a.md"), Optional.empty(), Optional.of("sha256:a"));
        Provenance provenance = new Provenance(
                new ProviderId("high"), Optional.of("1"), SourceLocator.file("specs/a.md"), Optional.of("R-1"),
                Optional.of("rev"), evidence.id());
        SpecificationId specificationId = SpecificationId.generate();
        RequirementId requirementId = RequirementId.generate();
        Requirement requirement = new Requirement(
                requirementId, specificationId, Optional.of("R-1"), "Requirement", "Statement", provenance);

        core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
        core.putSpecificationVersion(new SpecificationVersion(
                versionId, projectId, Optional.of(1L), Optional.of("provider-v1"), Optional.of("rev-1"), T0,
                Optional.empty()));
        core.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY, Optional.of("rev-1"), T0));
        core.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        core.putRequirementVersion(new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        EntityVersionId.generate(), requirementId.value(), versionId, TemporalState.CURRENT, requirement)));
        content.putSnapshotContent(new SnapshotBusinessContent(
                snapshotId, versionId,
                List.of(new Specification(specificationId, projectId, "spec-1", "Spec", Optional.empty(), provenance)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(evidence)));
        core.activateSnapshot(snapshotId, Optional.empty());
        return new Seeded(projectId, snapshotId);
    }

    private record Seeded(ProjectSpecificationId projectId, KnowledgeSnapshotId snapshotId) {
    }
}
