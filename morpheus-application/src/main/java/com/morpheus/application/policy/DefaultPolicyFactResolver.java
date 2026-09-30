package com.morpheus.application.policy;

import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.composition.CompositionEntityType;
import com.morpheus.application.composition.CompositionQueryService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationState;
import com.morpheus.application.quality.CoverageRatioStatus;
import com.morpheus.application.quality.QualityReportMetrics;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.query.ConstraintEvaluationQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.dsl.QueryScope;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.constraint.ConstraintEvaluationState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Production fact resolver for M25. It composes existing read-only application services and never performs mutation.
 */
public final class DefaultPolicyFactResolver implements PolicyFactResolver {
    private final ConstraintEvaluationQueryService constraints;
    private final ChangeTransitionEvaluationService lifecycle;
    private final QualityReportService quality;
    private final QueryExecutionService queries;
    private final CompositionQueryService compositions;

    public DefaultPolicyFactResolver(
            ConstraintEvaluationQueryService constraints,
            ChangeTransitionEvaluationService lifecycle,
            QualityReportService quality,
            QueryExecutionService queries,
            CompositionQueryService compositions) {
        this.constraints = Objects.requireNonNull(constraints, "constraints");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.compositions = Objects.requireNonNull(compositions, "compositions");
    }

    @Override
    public PolicyEvaluation.Fact resolve(PolicyScope scope, PolicyRule rule) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(rule, "rule");
        return switch (rule.kind()) {
            case CONSTRAINT_GUARD -> constraint(scope, (PolicyRule.ConstraintGuard) rule.config());
            case LIFECYCLE_GUARD -> lifecycle(scope, (PolicyRule.LifecycleGuard) rule.config());
            case QUALITY_THRESHOLD -> quality(scope, (PolicyRule.QualityThreshold) rule.config());
            case QUERY_ASSERTION -> query(scope, (PolicyRule.QueryAssertion) rule.config());
        };
    }

    private PolicyEvaluation.Fact constraint(PolicyScope scope, PolicyRule.ConstraintGuard config) {
        if (!(scope instanceof PolicyScope.Project project)) {
            return PolicyEvaluation.Fact.notApplicable("constraint guard requires project scope");
        }
        int budget = PolicyBudgets.MAX_CONSTRAINT_EVALUATIONS_PER_FACT;
        int offset = 0;
        boolean unknown = false;
        List<String> evidence = new ArrayList<>();
        while (true) {
            int limit = Math.min(PageRequest.MAX_LIMIT, budget - evidence.size() + 1);
            var page = constraints.activeEvaluations(
                    project.projectId(), config.changeId(), config.targetState(), new PageRequest(offset, limit));
            if (page.isEmpty()) {
                return PolicyEvaluation.Fact.unknown("no ACTIVE snapshot is available for constraint evaluation", List.of());
            }
            var value = page.orElseThrow();
            for (var evaluation : value.items()) {
                if (evidence.size() == budget) {
                    return PolicyEvaluation.Fact.unknown(
                            "EVALUATION_BUDGET_REACHED:" + budget
                                    + " constraints observed without an explicit blocking constraint; the rest of change "
                                    + config.changeId() + " was not observed",
                            evidence);
                }
                evidence.add("constraint:" + evaluation.constraintId());
                if (evaluation.state() == ConstraintEvaluationState.BLOCKING) {
                    return PolicyEvaluation.Fact.fail(
                            "explicit blocking constraint observed for lifecycle state " + config.targetState(), evidence);
                }
                if (evaluation.state() == ConstraintEvaluationState.UNKNOWN) {
                    unknown = true;
                }
            }
            if (!value.hasMore()) {
                break;
            }
            offset += value.items().size();
        }
        return unknown
                ? PolicyEvaluation.Fact.unknown("constraint blocking semantics are unknown", evidence)
                : PolicyEvaluation.Fact.pass("no explicit blocking constraint observed", evidence);
    }

    private PolicyEvaluation.Fact lifecycle(PolicyScope scope, PolicyRule.LifecycleGuard config) {
        if (!(scope instanceof PolicyScope.Project project)) {
            return PolicyEvaluation.Fact.notApplicable("lifecycle guard requires project scope");
        }
        var evaluation = lifecycle.evaluateActive(
                project.projectId(),
                ChangeLifecycle.of(config.changeId(), config.sourceState()),
                config.targetState(),
                ChangeLifecyclePolicy.forwardOnly(),
                Optional.empty());
        if (evaluation.isEmpty()) {
            return PolicyEvaluation.Fact.unknown("no ACTIVE snapshot is available for lifecycle evaluation", List.of());
        }
        var value = evaluation.orElseThrow();
        List<String> evidence = value.constraintEvaluations().stream()
                .map(item -> "constraint:" + item.constraintId())
                .sorted()
                .toList();
        return switch (value.state()) {
            case ALLOWED -> PolicyEvaluation.Fact.pass(value.reason(), evidence);
            case BLOCKED -> PolicyEvaluation.Fact.fail(value.reason(), evidence);
            case UNKNOWN, REQUIRES_INPUT -> PolicyEvaluation.Fact.unknown(value.reason(), evidence);
        };
    }

    private PolicyEvaluation.Fact quality(PolicyScope scope, PolicyRule.QualityThreshold config) {
        if (!(scope instanceof PolicyScope.Project project)) {
            return PolicyEvaluation.Fact.notApplicable("quality threshold requires project scope");
        }
        var report = quality.assessActive(project.projectId());
        if (report.isEmpty()) {
            return PolicyEvaluation.Fact.unknown("no ACTIVE snapshot is available for quality evaluation", List.of());
        }
        QualityReportMetrics metrics = report.orElseThrow().metrics();
        Optional<String> emptyPopulation = switch (config.metric()) {
            case REQUIREMENT_COVERAGE_PERCENT -> undefined(metrics.requirementCoverageStatus(), "CURRENT requirement");
            case TASK_COVERAGE_PERCENT -> undefined(metrics.taskCoverageStatus(), "implementation task");
            case FINDINGS, ORPHAN_REQUIREMENTS, UNCOVERED_TASKS, CHANGES, DECISIONS, EXTERNAL_REFERENCES ->
                    Optional.empty();
        };
        if (emptyPopulation.isPresent()) {
            return PolicyEvaluation.Fact.unknown(
                    "quality metric " + config.metric() + " is undefined: the ACTIVE snapshot has no "
                            + emptyPopulation.orElseThrow(),
                    List.of("quality:active-snapshot"));
        }
        Optional<String> duplicated = duplicatedPopulation(project, config.metric());
        if (duplicated.isPresent()) {
            return PolicyEvaluation.Fact.unknown(
                    "quality metric " + config.metric() + " is undefined: the composed ACTIVE snapshot " + duplicated.orElseThrow(),
                    List.of("quality:active-snapshot", "composition:active-snapshot"));
        }
        double actual = switch (config.metric()) {
            case FINDINGS -> metrics.totalFindings();
            case ORPHAN_REQUIREMENTS -> metrics.orphanRequirements();
            case UNCOVERED_TASKS -> metrics.uncoveredTasks();
            case REQUIREMENT_COVERAGE_PERCENT -> metrics.requirementCoverageRatio() * 100.0;
            case TASK_COVERAGE_PERCENT -> metrics.taskCoverageRatio() * 100.0;
            case CHANGES -> metrics.totalChanges();
            case DECISIONS -> metrics.totalDesignDecisions();
            case EXTERNAL_REFERENCES -> metrics.totalExternalReferences();
        };
        boolean pass = config.comparison().test(actual, config.threshold());
        return PolicyEvaluation.Fact.measured(
                pass,
                actual,
                "quality metric " + config.metric() + "=" + actual + " " + config.comparison() + " " + config.threshold(),
                List.of("quality:active-snapshot"));
    }

    /**
     * A coverage ratio over an empty population is not a measurement; {@link QualityReportMetrics} decides which is
     * which, and this resolver only names the population for the reason. Counts are left alone: zero orphans among
     * zero requirements is a true zero.
     */
    private static Optional<String> undefined(CoverageRatioStatus status, String population) {
        return status == CoverageRatioStatus.MEASURED ? Optional.empty() : Optional.of(population);
    }

    /**
     * A ratio is a measurement only over a population that is counted once. A multi-provider composition publishes
     * each provider's entity separately, so a logical key observed by two providers is in the denominator twice and
     * the ratio is computed on a population known to be wrong. The composition state says which entity types carry
     * such duplicates; a ratio over one of them is UNKNOWN rather than a number. Counts stay counts: they say how
     * many entities are published, which is true.
     */
    private Optional<String> duplicatedPopulation(PolicyScope.Project project, PolicyRule.QualityMetric metric) {
        CompositionEntityType type = switch (metric) {
            case REQUIREMENT_COVERAGE_PERCENT -> CompositionEntityType.REQUIREMENT;
            case TASK_COVERAGE_PERCENT -> CompositionEntityType.TASK;
            case FINDINGS, ORPHAN_REQUIREMENTS, UNCOVERED_TASKS, CHANGES, DECISIONS, EXTERNAL_REFERENCES -> null;
        };
        if (type == null) {
            return Optional.empty();
        }
        return compositions.findActive(project.projectId())
                .map(state -> state.conflicts().stream()
                        .filter(conflict -> conflict.entityType().equals(type.name()))
                        .map(conflict -> conflict.logicalKey())
                        .distinct()
                        .count())
                .filter(keys -> keys > 0)
                .map(keys -> "publishes " + keys + " duplicated " + type.name() + " key(s) observed by more than one provider");
    }

    private PolicyEvaluation.Fact query(PolicyScope scope, PolicyRule.QueryAssertion config) {
        QueryScope expected = switch (scope) {
            case PolicyScope.Project project -> new ProjectQueryScope(project.projectId());
            case PolicyScope.Portfolio portfolio -> new PortfolioQueryScope(portfolio.portfolioId());
        };
        if (!config.query().scope().equals(expected)) {
            return PolicyEvaluation.Fact.notApplicable("query assertion scope does not match policy evaluation scope");
        }
        var result = queries.execute(config.query());
        double actual = result.totalMatches();
        boolean pass = config.comparison().test(actual, config.expectedCount());
        return PolicyEvaluation.Fact.measured(
                pass,
                actual,
                "query totalMatches=" + result.totalMatches() + " " + config.comparison() + " " + config.expectedCount(),
                List.of("query:" + config.query().entityType().name()));
    }
}