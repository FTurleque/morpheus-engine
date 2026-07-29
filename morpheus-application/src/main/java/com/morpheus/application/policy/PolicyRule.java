package com.morpheus.application.policy;

import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;

import java.util.Objects;

/** Closed, typed and provider-neutral M25 policy rule. No arbitrary code or SQL is executable. */
public record PolicyRule(
        PolicyIds.RuleId id,
        String description,
        Kind kind,
        Severity severity,
        Config config) implements Comparable<PolicyRule> {

    public PolicyRule {
        Objects.requireNonNull(id, "id");
        description = requireBounded(description, "description", PolicyBudgets.MAX_RULE_DESCRIPTION);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(config, "config");
        if (!kind.accepts(config)) {
            throw new IllegalArgumentException("policy rule kind/config mismatch: " + kind);
        }
    }

    @Override
    public int compareTo(PolicyRule other) {
        return id.compareTo(other.id);
    }

    public enum Kind {
        CONSTRAINT_GUARD(ConstraintGuard.class),
        LIFECYCLE_GUARD(LifecycleGuard.class),
        QUALITY_THRESHOLD(QualityThreshold.class),
        QUERY_ASSERTION(QueryAssertion.class);

        private final Class<? extends Config> configType;

        Kind(Class<? extends Config> configType) {
            this.configType = configType;
        }

        boolean accepts(Config config) {
            return configType.isInstance(config);
        }
    }

    public enum Severity {
        INFO,
        WARNING,
        BLOCKER
    }

    public enum Comparison {
        EQ,
        NE,
        LT,
        LTE,
        GT,
        GTE;

        public boolean test(double actual, double expected) {
            return switch (this) {
                case EQ -> Double.compare(actual, expected) == 0;
                case NE -> Double.compare(actual, expected) != 0;
                case LT -> actual < expected;
                case LTE -> actual <= expected;
                case GT -> actual > expected;
                case GTE -> actual >= expected;
            };
        }
    }

    public enum QualityMetric {
        FINDINGS,
        ORPHAN_REQUIREMENTS,
        UNCOVERED_TASKS,
        REQUIREMENT_COVERAGE_PERCENT,
        TASK_COVERAGE_PERCENT,
        CHANGES,
        DECISIONS,
        EXTERNAL_REFERENCES
    }

    public sealed interface Config permits ConstraintGuard, LifecycleGuard, QualityThreshold, QueryAssertion {
    }

    public record ConstraintGuard(ChangeId changeId, ChangeLifecycleState targetState) implements Config {
        public ConstraintGuard {
            Objects.requireNonNull(changeId, "changeId");
            Objects.requireNonNull(targetState, "targetState");
        }
    }

    public record LifecycleGuard(
            ChangeId changeId,
            ChangeLifecycleState sourceState,
            ChangeLifecycleState targetState) implements Config {
        public LifecycleGuard {
            Objects.requireNonNull(changeId, "changeId");
            Objects.requireNonNull(sourceState, "sourceState");
            Objects.requireNonNull(targetState, "targetState");
            if (sourceState == targetState) {
                throw new IllegalArgumentException("lifecycle guard source and target must differ");
            }
        }
    }

    public record QualityThreshold(
            QualityMetric metric,
            Comparison comparison,
            double threshold) implements Config {
        public QualityThreshold {
            Objects.requireNonNull(metric, "metric");
            Objects.requireNonNull(comparison, "comparison");
            if (!Double.isFinite(threshold)) {
                throw new IllegalArgumentException("quality threshold must be finite");
            }
        }
    }

    public record QueryAssertion(
            QueryDefinition query,
            Comparison comparison,
            long expectedCount) implements Config {
        public QueryAssertion {
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(comparison, "comparison");
            if (expectedCount < 0) {
                throw new IllegalArgumentException("query assertion expectedCount must be >= 0");
            }
        }
    }

    private static String requireBounded(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}