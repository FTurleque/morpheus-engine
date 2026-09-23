package com.morpheus.architecture;

import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.policy.DefaultPolicyFactResolver;
import com.morpheus.application.policy.PolicyEvaluation;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.query.ConstraintEvaluationQueryService;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemoryExternalReferenceStore;
import com.morpheus.store.memory.MemoryPortfolioStore;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A coverage ratio over an empty population is not a measurement, so a policy threshold on it is UNKNOWN; a count
 * over the same empty population is a true zero and keeps its verdict. A project whose ingestion produced nothing
 * used to pass "coverage >= 80%" with 100%.
 */
class EmptyPopulationQualityPolicyContractTest {
    private static final Instant T0 = Instant.parse("2026-09-23T08:00:00Z");

    private final MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
    private final MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
    private final MemoryTraceabilityStore traceability = new MemoryTraceabilityStore(core);
    private final DefaultPolicyFactResolver resolver = resolver();

    @Test
    void anEmptyRequirementPopulationIsUnknownNotFullCoverage() {
        ProjectSpecificationId projectId = emptyActiveProject();

        PolicyEvaluation.Fact fact = resolve(projectId, PolicyRule.QualityMetric.REQUIREMENT_COVERAGE_PERCENT, 80.0);

        assertEquals(PolicyEvaluation.FactState.UNKNOWN, fact.state());
        assertEquals(PolicyEvaluation.Applicability.UNKNOWN, fact.applicability());
        assertTrue(fact.observedValue().isEmpty(), "an undefined ratio must not carry an observed value");
        assertTrue(fact.reason().contains("no CURRENT requirement"), fact.reason());
    }

    @Test
    void anEmptyTaskPopulationIsUnknownNotFullCoverage() {
        ProjectSpecificationId projectId = emptyActiveProject();

        PolicyEvaluation.Fact fact = resolve(projectId, PolicyRule.QualityMetric.TASK_COVERAGE_PERCENT, 80.0);

        assertEquals(PolicyEvaluation.FactState.UNKNOWN, fact.state());
        assertTrue(fact.reason().contains("no implementation task"), fact.reason());
    }

    @Test
    void anEmptyPopulationStillCountsZeroOrphansAsAPass() {
        ProjectSpecificationId projectId = emptyActiveProject();

        PolicyEvaluation.Fact fact = resolve(projectId, PolicyRule.QualityMetric.ORPHAN_REQUIREMENTS, 0.0);

        assertEquals(PolicyEvaluation.FactState.PASS, fact.state());
        assertEquals(0.0, fact.observedValue().orElseThrow());
    }

    private PolicyEvaluation.Fact resolve(
            ProjectSpecificationId projectId,
            PolicyRule.QualityMetric metric,
            double threshold) {
        PolicyRule.Comparison comparison = metric == PolicyRule.QualityMetric.ORPHAN_REQUIREMENTS
                ? PolicyRule.Comparison.LTE
                : PolicyRule.Comparison.GTE;
        PolicyRule rule = new PolicyRule(
                PolicyIds.RuleId.generate(),
                "Quality threshold on " + metric,
                PolicyRule.Kind.QUALITY_THRESHOLD,
                PolicyRule.Severity.BLOCKER,
                new PolicyRule.QualityThreshold(metric, comparison, threshold));
        return resolver.resolve(new PolicyScope.Project(projectId), rule);
    }

    private ProjectSpecificationId emptyActiveProject() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
        core.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY, Optional.of("rev-1"), T0));
        core.putSpecificationVersion(new SpecificationVersion(
                versionId, projectId, Optional.of(1L), Optional.of("empty"), Optional.of("rev-1"), T0, Optional.empty()));
        core.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        content.putSnapshotContent(new SnapshotBusinessContent(
                snapshotId, versionId, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of()));
        core.activateSnapshot(snapshotId, Optional.empty());
        return projectId;
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
                new QueryExecutionService(core, core, content, new MemoryPortfolioStore()));
    }
}
