package com.morpheus.application.policy;

import com.morpheus.application.composition.CompositionQueryService;
import com.morpheus.application.composition.CompositionSnapshotState;
import com.morpheus.application.composition.CompositionStateStore;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.query.ConstraintEvaluationQueryService;
import com.morpheus.application.query.PublishedSnapshotWorld;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A quality threshold is a fact about the ACTIVE snapshot of a project: a count is measured even when it is zero, a
 * coverage ratio over an empty population is UNKNOWN, and a portfolio scope does not apply.
 */
class QualityThresholdFactTest {
    private final PublishedSnapshotWorld world = new PublishedSnapshotWorld();
    private final DefaultPolicyFactResolver resolver = resolver(world);

    @Test
    void aCountIsMeasuredAndComparedWithItsThreshold() {
        world.change("one");
        world.change("two");

        PolicyEvaluation.Fact within = resolve(PolicyRule.QualityMetric.CHANGES, PolicyRule.Comparison.LTE, 2);
        PolicyEvaluation.Fact beyond = resolve(PolicyRule.QualityMetric.CHANGES, PolicyRule.Comparison.LT, 2);

        assertEquals(PolicyEvaluation.FactState.PASS, within.state());
        assertEquals(OptionalDouble.of(2), within.observedValue());
        assertEquals("quality metric CHANGES=2.0 LTE 2.0", within.reason());
        assertEquals(List.of("quality:active-snapshot"), within.evidence());
        assertEquals(PolicyEvaluation.FactState.FAIL, beyond.state());
    }

    @Test
    void everyCountMetricIsMeasuredOnAnEmptySnapshot() {
        for (PolicyRule.QualityMetric metric : List.of(PolicyRule.QualityMetric.FINDINGS,
                PolicyRule.QualityMetric.ORPHAN_REQUIREMENTS, PolicyRule.QualityMetric.UNCOVERED_TASKS,
                PolicyRule.QualityMetric.DECISIONS, PolicyRule.QualityMetric.EXTERNAL_REFERENCES)) {
            PolicyEvaluation.Fact fact = resolve(metric, PolicyRule.Comparison.EQ, 0);

            assertEquals(PolicyEvaluation.FactState.PASS, fact.state(), metric.name());
            assertEquals(OptionalDouble.of(0), fact.observedValue(), metric.name());
        }
    }

    @Test
    void aCoverageRatioOverAnEmptyPopulationIsUnknownAndNamesThePopulation() {
        PolicyEvaluation.Fact requirements = resolve(
                PolicyRule.QualityMetric.REQUIREMENT_COVERAGE_PERCENT, PolicyRule.Comparison.GTE, 80);
        PolicyEvaluation.Fact tasks = resolve(
                PolicyRule.QualityMetric.TASK_COVERAGE_PERCENT, PolicyRule.Comparison.GTE, 80);

        assertEquals(PolicyEvaluation.FactState.UNKNOWN, requirements.state());
        assertEquals("quality metric REQUIREMENT_COVERAGE_PERCENT is undefined: the ACTIVE snapshot has no CURRENT"
                + " requirement", requirements.reason());
        assertEquals(PolicyEvaluation.FactState.UNKNOWN, tasks.state());
        assertEquals("quality metric TASK_COVERAGE_PERCENT is undefined: the ACTIVE snapshot has no implementation"
                + " task", tasks.reason());
    }

    @Test
    void aCoverageRatioOverAPopulationIsAPercentage() {
        world.task(world.change("change").id());

        PolicyEvaluation.Fact fact = resolve(
                PolicyRule.QualityMetric.TASK_COVERAGE_PERCENT, PolicyRule.Comparison.GTE, 50);

        assertEquals(PolicyEvaluation.FactState.FAIL, fact.state());
        assertEquals(OptionalDouble.of(0), fact.observedValue());
    }

    @Test
    void aProjectWithoutAnActiveSnapshotIsUnknownAndAPortfolioDoesNotApply() {
        PolicyRule rule = rule(PolicyRule.QualityMetric.CHANGES, PolicyRule.Comparison.EQ, 0);

        PolicyEvaluation.Fact unpublished = resolver.resolve(
                new PolicyScope.Project(ProjectSpecificationId.generate()), rule);
        PolicyEvaluation.Fact portfolio = resolver.resolve(new PolicyScope.Portfolio(PortfolioId.generate()), rule);

        assertEquals(PolicyEvaluation.FactState.UNKNOWN, unpublished.state());
        assertEquals("no ACTIVE snapshot is available for quality evaluation", unpublished.reason());
        assertEquals(PolicyEvaluation.FactState.NOT_APPLICABLE, portfolio.state());
        assertEquals("quality threshold requires project scope", portfolio.reason());
    }

    private PolicyEvaluation.Fact resolve(
            PolicyRule.QualityMetric metric,
            PolicyRule.Comparison comparison,
            double threshold) {
        return resolver.resolve(new PolicyScope.Project(world.projectId()), rule(metric, comparison, threshold));
    }

    private static PolicyRule rule(PolicyRule.QualityMetric metric, PolicyRule.Comparison comparison, double threshold) {
        return new PolicyRule(PolicyIds.RuleId.generate(), "threshold", PolicyRule.Kind.QUALITY_THRESHOLD,
                PolicyRule.Severity.WARNING, new PolicyRule.QualityThreshold(metric, comparison, threshold));
    }

    private static DefaultPolicyFactResolver resolver(PublishedSnapshotWorld world) {
        var snapshots = world.snapshots();
        var content = world.contentStore();
        var requirements = world.requirementStore();
        var links = world.traceabilityStore();
        QualityReportService quality = new QualityReportService(
                snapshots,
                new RequirementQualityService(snapshots, requirements, links),
                new TaskQualityService(snapshots, content, requirements, links),
                new AcceptanceQualityService(snapshots, content),
                new ChangeCompletenessService(snapshots, content, requirements, links),
                new DecisionReferenceQualityService(snapshots, content, requirements, links, world.referenceStore()));
        return new DefaultPolicyFactResolver(
                new ConstraintEvaluationQueryService(snapshots, content),
                new ChangeTransitionEvaluationService(snapshots, content, requirements, links),
                quality,
                new QueryExecutionService(snapshots, requirements, content, unusedPortfolios()),
                new CompositionQueryService(snapshots, new NoComposition()));
    }

    private static PortfolioStore unusedPortfolios() {
        return (PortfolioStore) Proxy.newProxyInstance(
                QualityThresholdFactTest.class.getClassLoader(), new Class<?>[]{PortfolioStore.class},
                (proxy, method, args) -> {
                    throw new AssertionError("a quality threshold must not read PortfolioStore." + method.getName());
                });
    }

    private static final class NoComposition implements CompositionStateStore {
        @Override
        public void save(CompositionSnapshotState state) {
            throw new UnsupportedOperationException("a quality threshold only reads");
        }

        @Override
        public Optional<CompositionSnapshotState> find(KnowledgeSnapshotId snapshotId) {
            return Optional.empty();
        }
    }
}
