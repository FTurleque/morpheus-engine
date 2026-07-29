package com.morpheus.application.policy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Explainable M25 evaluation preserving source and effective decisions. */
public final class PolicyEvaluation {
    private PolicyEvaluation() {
    }

    public enum Applicability {
        APPLICABLE,
        NOT_APPLICABLE,
        UNKNOWN
    }

    public enum Decision {
        PASS,
        WARN,
        BLOCK,
        UNKNOWN
    }

    public enum FactState {
        PASS,
        FAIL,
        UNKNOWN,
        NOT_APPLICABLE
    }

    public record Fact(
            Applicability applicability,
            FactState state,
            String reason,
            OptionalDouble observedValue,
            List<String> evidence) {
        public Fact {
            Objects.requireNonNull(applicability, "applicability");
            Objects.requireNonNull(state, "state");
            reason = nonBlank(reason, "reason");
            Objects.requireNonNull(observedValue, "observedValue");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence").stream().sorted().toList());
            if (applicability == Applicability.NOT_APPLICABLE && state != FactState.NOT_APPLICABLE) {
                throw new IllegalArgumentException("NOT_APPLICABLE fact must use NOT_APPLICABLE state");
            }
        }

        public static Fact pass(String reason, List<String> evidence) {
            return new Fact(Applicability.APPLICABLE, FactState.PASS, reason, OptionalDouble.empty(), evidence);
        }

        public static Fact fail(String reason, List<String> evidence) {
            return new Fact(Applicability.APPLICABLE, FactState.FAIL, reason, OptionalDouble.empty(), evidence);
        }

        public static Fact measured(boolean pass, double value, String reason, List<String> evidence) {
            return new Fact(Applicability.APPLICABLE, pass ? FactState.PASS : FactState.FAIL,
                    reason, OptionalDouble.of(value), evidence);
        }

        public static Fact unknown(String reason, List<String> evidence) {
            return new Fact(Applicability.UNKNOWN, FactState.UNKNOWN, reason, OptionalDouble.empty(), evidence);
        }

        public static Fact notApplicable(String reason) {
            return new Fact(Applicability.NOT_APPLICABLE, FactState.NOT_APPLICABLE,
                    reason, OptionalDouble.empty(), List.of());
        }
    }

    public record RuleResult(
            PolicyIds.RuleId ruleId,
            PolicyRule.Kind kind,
            PolicyRule.Severity severity,
            Applicability applicability,
            Decision originalDecision,
            Decision effectiveDecision,
            String reason,
            Optional<PolicyConfiguration.Override> appliedOverride,
            OptionalDouble observedValue,
            List<String> evidence) implements Comparable<RuleResult> {
        public RuleResult {
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(applicability, "applicability");
            Objects.requireNonNull(originalDecision, "originalDecision");
            Objects.requireNonNull(effectiveDecision, "effectiveDecision");
            reason = nonBlank(reason, "reason");
            Objects.requireNonNull(appliedOverride, "appliedOverride");
            Objects.requireNonNull(observedValue, "observedValue");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence").stream().sorted().toList());
        }

        @Override
        public int compareTo(RuleResult other) {
            return ruleId.compareTo(other.ruleId);
        }
    }

    public record Report(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.VersionId versionId,
            long versionNumber,
            boolean dryRun,
            Decision decision,
            List<RuleResult> rules) implements Comparable<Report> {
        public Report {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(versionId, "versionId");
            if (versionNumber <= 0) {
                throw new IllegalArgumentException("versionNumber must be positive");
            }
            Objects.requireNonNull(decision, "decision");
            rules = List.copyOf(Objects.requireNonNull(rules, "rules").stream().sorted().toList());
        }

        @Override
        public int compareTo(Report other) {
            return packId.compareTo(other.packId);
        }
    }

    public record GovernanceReport(PolicyScope scope, List<Report> packs, Decision decision) {
        public GovernanceReport {
            Objects.requireNonNull(scope, "scope");
            packs = List.copyOf(Objects.requireNonNull(packs, "packs").stream().sorted().toList());
            Objects.requireNonNull(decision, "decision");
        }
    }

    private static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}