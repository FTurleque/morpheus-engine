package com.morpheus.application.policy;

import com.morpheus.application.composition.CompositionQueryService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.query.ConstraintEvaluationQueryService;
import com.morpheus.application.query.ConstraintSnapshotFixture;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.store.CompositionStateStore;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A constraint guard that could not observe every constraint of its change does not know that none of them blocks.
 * Past {@link PolicyBudgets#MAX_CONSTRAINT_EVALUATIONS_PER_FACT} the fact is UNKNOWN, never PASS (ADR-0078,
 * ADR-0093); a blocking constraint observed before the budget still fails the fact.
 */
class ConstraintGuardBudgetTest {
    private static final int BUDGET = PolicyBudgets.MAX_CONSTRAINT_EVALUATIONS_PER_FACT;

    @Test
    void aConstraintWalkPastItsBudgetIsUnknownRatherThanPass() {
        ConstraintSnapshotFixture fixture = ConstraintSnapshotFixture.nonBlocking(BUDGET + 1);

        PolicyEvaluation.Fact fact = resolve(fixture);

        assertEquals(PolicyEvaluation.FactState.UNKNOWN, fact.state());
        assertEquals(PolicyEvaluation.Applicability.UNKNOWN, fact.applicability());
        assertTrue(fact.reason().startsWith("EVALUATION_BUDGET_REACHED:" + BUDGET), fact.reason());
        assertEquals(BUDGET, fact.evidence().size(), "evidence must stop growing at the budget");
    }

    @Test
    void aWalkThatObservesEveryConstraintWithinItsBudgetStillPasses() {
        ConstraintSnapshotFixture fixture = ConstraintSnapshotFixture.nonBlocking(BUDGET);

        PolicyEvaluation.Fact fact = resolve(fixture);

        assertEquals(PolicyEvaluation.FactState.PASS, fact.state());
        assertEquals(BUDGET, fact.evidence().size());
    }

    @Test
    void aBlockingConstraintSeenBeforeTheBudgetStillFails() {
        ConstraintSnapshotFixture fixture = ConstraintSnapshotFixture.blockingAt(BUDGET + 50, BUDGET - 1);

        PolicyEvaluation.Fact fact = resolve(fixture);

        assertEquals(PolicyEvaluation.FactState.FAIL, fact.state());
        assertTrue(fact.reason().startsWith("explicit blocking constraint observed"), fact.reason());
        assertEquals(BUDGET, fact.evidence().size());
    }

    private static PolicyEvaluation.Fact resolve(ConstraintSnapshotFixture fixture) {
        DefaultPolicyFactResolver resolver = new DefaultPolicyFactResolver(
                new ConstraintEvaluationQueryService(fixture.snapshots(), fixture.content()),
                new ChangeTransitionEvaluationService(
                        unusedPort(SpecificationKnowledgeStore.class),
                        unusedPort(SnapshotBusinessContentStore.class),
                        unusedPort(VersionedRequirementStore.class),
                        unusedPort(TraceabilityStore.class)),
                unusedQuality(),
                new QueryExecutionService(
                        unusedPort(SpecificationKnowledgeStore.class),
                        unusedPort(VersionedRequirementStore.class),
                        unusedPort(SnapshotBusinessContentStore.class),
                        unusedPort(PortfolioStore.class)),
                new CompositionQueryService(
                        unusedPort(SpecificationKnowledgeStore.class), unusedPort(CompositionStateStore.class)));
        PolicyRule rule = new PolicyRule(
                PolicyIds.RuleId.generate(),
                "No explicit blockers",
                PolicyRule.Kind.CONSTRAINT_GUARD,
                PolicyRule.Severity.BLOCKER,
                new PolicyRule.ConstraintGuard(fixture.changeId(), ConstraintSnapshotFixture.TARGET));
        return resolver.resolve(new PolicyScope.Project(fixture.projectId()), rule);
    }

    private static QualityReportService unusedQuality() {
        SpecificationKnowledgeStore snapshots = unusedPort(SpecificationKnowledgeStore.class);
        VersionedRequirementStore requirements = unusedPort(VersionedRequirementStore.class);
        SnapshotBusinessContentStore content = unusedPort(SnapshotBusinessContentStore.class);
        TraceabilityStore traceability = unusedPort(TraceabilityStore.class);
        return new QualityReportService(
                snapshots,
                new RequirementQualityService(snapshots, requirements, traceability),
                new TaskQualityService(snapshots, content, requirements, traceability),
                new AcceptanceQualityService(snapshots, content),
                new ChangeCompletenessService(snapshots, content, requirements, traceability),
                new DecisionReferenceQualityService(
                        snapshots, content, requirements, traceability, unusedPort(ExternalReferenceStore.class)));
    }

    private static <T> T unusedPort(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                ConstraintGuardBudgetTest.class.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    throw new AssertionError("a constraint guard must not call "
                            + type.getSimpleName() + "." + method.getName());
                }));
    }
}
