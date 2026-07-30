package com.morpheus.application.policy;

import com.morpheus.application.store.PolicyPackStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic read-only M25 governance evaluator. Evaluation and dry-run never perform mutation. */
public final class PolicyEvaluationService {
    private final PolicyPackStore store;
    private final PolicyFactResolver facts;

    public PolicyEvaluationService(PolicyPackStore store, PolicyFactResolver facts) {
        this.store = Objects.requireNonNull(store, "store");
        this.facts = Objects.requireNonNull(facts, "facts");
    }

    public PolicyEvaluation.GovernanceReport evaluate(PolicyScope scope) {
        List<PolicyConfiguration.Activation> activations = store.listActivations(scope).stream().sorted().toList();
        if (activations.size() > PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE) {
            throw new IllegalStateException("persisted active policy packs exceed configured budget");
        }
        int totalRules = activations.stream()
                .map(activation -> requireVersion(activation.packId(), activation.versionId()))
                .mapToInt(version -> version.rules().size())
                .sum();
        if (totalRules > PolicyBudgets.MAX_DRY_RUN_EVALUATIONS) {
            throw new IllegalArgumentException(
                    "policy evaluation exceeds rule budget: " + PolicyBudgets.MAX_DRY_RUN_EVALUATIONS);
        }
        List<PolicyEvaluation.Report> reports = activations.stream()
                .map(activation -> evaluateVersion(scope, requireVersion(activation.packId(), activation.versionId()), false))
                .sorted()
                .toList();
        return new PolicyEvaluation.GovernanceReport(scope, reports, aggregateReports(reports));
    }

    public PolicyEvaluation.Report evaluatePack(PolicyScope scope, PolicyIds.PackId packId) {
        PolicyConfiguration.Activation activation = store.findActivation(scope, packId)
                .orElseThrow(() -> new IllegalArgumentException("policy pack is not active in scope: " + packId));
        return evaluateVersion(scope, requireVersion(packId, activation.versionId()), false);
    }

    public PolicyEvaluation.Report dryRun(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.VersionId versionId) {
        PolicyPack.Version version = requireVersion(packId, versionId);
        if (version.rules().size() > PolicyBudgets.MAX_DRY_RUN_EVALUATIONS) {
            throw new IllegalArgumentException(
                    "dry-run exceeds rule budget: " + PolicyBudgets.MAX_DRY_RUN_EVALUATIONS);
        }
        return evaluateVersion(scope, version, true);
    }

    private PolicyEvaluation.Report evaluateVersion(
            PolicyScope scope,
            PolicyPack.Version version,
            boolean dryRun) {
        List<PolicyEvaluation.RuleResult> results = new ArrayList<>(version.rules().size());
        for (PolicyRule rule : version.rules()) {
            PolicyEvaluation.Fact fact = facts.resolve(scope, rule);
            PolicyEvaluation.Decision original = sourceDecision(rule, fact);
            Optional<PolicyConfiguration.Override> override = store.findOverride(scope, version.packId(), rule.id());
            PolicyEvaluation.Decision effective = applyOverride(original, override);
            String reason = fact.reason();
            if (override.isPresent()) {
                PolicyConfiguration.Override applied = override.orElseThrow();
                reason = reason + "; override=" + applied.mode() + " by " + applied.actor()
                        + " because " + applied.reason();
            }
            results.add(new PolicyEvaluation.RuleResult(
                    rule.id(),
                    rule.kind(),
                    rule.severity(),
                    fact.applicability(),
                    original,
                    effective,
                    reason,
                    override,
                    fact.observedValue(),
                    fact.evidence()));
        }
        List<PolicyEvaluation.RuleResult> ordered = results.stream().sorted().toList();
        return new PolicyEvaluation.Report(
                scope,
                version.packId(),
                version.versionId(),
                version.versionNumber(),
                dryRun,
                aggregateRules(ordered),
                ordered);
    }

    private PolicyPack.Version requireVersion(PolicyIds.PackId packId, PolicyIds.VersionId versionId) {
        return store.findVersion(packId, versionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown policy pack version: " + packId + "/" + versionId));
    }

    private PolicyEvaluation.Decision sourceDecision(PolicyRule rule, PolicyEvaluation.Fact fact) {
        return switch (fact.state()) {
            case PASS, NOT_APPLICABLE -> PolicyEvaluation.Decision.PASS;
            case UNKNOWN -> PolicyEvaluation.Decision.UNKNOWN;
            case FAIL -> rule.severity() == PolicyRule.Severity.BLOCKER
                    ? PolicyEvaluation.Decision.BLOCK
                    : PolicyEvaluation.Decision.WARN;
        };
    }

    private PolicyEvaluation.Decision applyOverride(
            PolicyEvaluation.Decision original,
            Optional<PolicyConfiguration.Override> override) {
        if (override.isEmpty()) {
            return original;
        }
        return switch (override.orElseThrow().mode()) {
            case DISABLE -> PolicyEvaluation.Decision.PASS;
            case FORCE_WARN -> PolicyEvaluation.Decision.WARN;
            case FORCE_BLOCK -> PolicyEvaluation.Decision.BLOCK;
        };
    }

    private PolicyEvaluation.Decision aggregateRules(List<PolicyEvaluation.RuleResult> rules) {
        if (rules.stream().anyMatch(rule -> rule.effectiveDecision() == PolicyEvaluation.Decision.BLOCK)) {
            return PolicyEvaluation.Decision.BLOCK;
        }
        if (rules.stream().anyMatch(rule -> rule.effectiveDecision() == PolicyEvaluation.Decision.UNKNOWN)) {
            return PolicyEvaluation.Decision.UNKNOWN;
        }
        if (rules.stream().anyMatch(rule -> rule.effectiveDecision() == PolicyEvaluation.Decision.WARN)) {
            return PolicyEvaluation.Decision.WARN;
        }
        return PolicyEvaluation.Decision.PASS;
    }

    private PolicyEvaluation.Decision aggregateReports(List<PolicyEvaluation.Report> reports) {
        if (reports.stream().anyMatch(report -> report.decision() == PolicyEvaluation.Decision.BLOCK)) {
            return PolicyEvaluation.Decision.BLOCK;
        }
        if (reports.stream().anyMatch(report -> report.decision() == PolicyEvaluation.Decision.UNKNOWN)) {
            return PolicyEvaluation.Decision.UNKNOWN;
        }
        if (reports.stream().anyMatch(report -> report.decision() == PolicyEvaluation.Decision.WARN)) {
            return PolicyEvaluation.Decision.WARN;
        }
        return PolicyEvaluation.Decision.PASS;
    }
}