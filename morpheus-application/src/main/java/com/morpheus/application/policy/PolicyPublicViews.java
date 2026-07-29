package com.morpheus.application.policy;

import com.morpheus.application.query.dsl.QueryPublicViews;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Transport-safe M25 projections shared by CLI, MCP and HTTP. */
public final class PolicyPublicViews {
    private PolicyPublicViews() {
    }

    public static DefinitionView definition(PolicyPack.Definition value) {
        Objects.requireNonNull(value, "value");
        return new DefinitionView(
                value.id().toString(), value.name(), value.revision(), value.latestVersionNumber(),
                value.createdAt().toString(), value.updatedAt().toString());
    }

    public static List<DefinitionView> definitions(List<PolicyPack.Definition> values) {
        return List.copyOf(values).stream().map(PolicyPublicViews::definition).toList();
    }

    public static VersionView version(PolicyPack.Version value) {
        Objects.requireNonNull(value, "value");
        return new VersionView(
                value.packId().toString(), value.versionId().toString(), value.versionNumber(), value.name(),
                value.rules().stream().map(PolicyPublicViews::rule).toList(), value.createdAt().toString());
    }

    public static List<VersionView> versions(List<PolicyPack.Version> values) {
        return List.copyOf(values).stream().map(PolicyPublicViews::version).toList();
    }

    public static ActivationView activation(PolicyConfiguration.Activation value) {
        return new ActivationView(
                scope(value.scope()), value.packId().toString(), value.versionId().toString(), value.revision(),
                value.actor(), value.updatedAt().toString());
    }

    public static List<ActivationView> activations(List<PolicyConfiguration.Activation> values) {
        return List.copyOf(values).stream().map(PolicyPublicViews::activation).toList();
    }

    public static OverrideView override(PolicyConfiguration.Override value) {
        return new OverrideView(
                scope(value.scope()), value.packId().toString(), value.ruleId().toString(), value.mode().name(),
                value.reason(), value.actor(), value.revision(), value.updatedAt().toString());
    }

    public static List<OverrideView> overrides(List<PolicyConfiguration.Override> values) {
        return List.copyOf(values).stream().map(PolicyPublicViews::override).toList();
    }

    public static AuditView audit(PolicyConfiguration.AuditRecord value) {
        return new AuditView(
                value.id().toString(), value.action().name(), value.packId().toString(),
                value.versionId().map(Object::toString), value.ruleId().map(Object::toString),
                value.scope().map(PolicyPublicViews::scope), value.actor(), value.reason(), value.at().toString());
    }

    public static List<AuditView> audit(List<PolicyConfiguration.AuditRecord> values) {
        return List.copyOf(values).stream().map(PolicyPublicViews::audit).toList();
    }

    public static ReportView report(PolicyEvaluation.Report value) {
        return new ReportView(
                scope(value.scope()), value.packId().toString(), value.versionId().toString(), value.versionNumber(),
                value.dryRun(), value.decision().name(), value.rules().stream().map(PolicyPublicViews::ruleResult).toList());
    }

    public static GovernanceReportView governance(PolicyEvaluation.GovernanceReport value) {
        return new GovernanceReportView(
                scope(value.scope()), value.decision().name(), value.packs().stream().map(PolicyPublicViews::report).toList());
    }

    private static RuleView rule(PolicyRule value) {
        return new RuleView(
                value.id().toString(), value.description(), value.kind().name(), value.severity().name(), config(value.config()));
    }

    private static ConfigView config(PolicyRule.Config value) {
        return switch (value) {
            case PolicyRule.ConstraintGuard c -> new ConfigView(
                    Optional.of(c.changeId().toString()), Optional.empty(), Optional.of(c.targetState().name()),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
            case PolicyRule.LifecycleGuard c -> new ConfigView(
                    Optional.of(c.changeId().toString()), Optional.of(c.sourceState().name()), Optional.of(c.targetState().name()),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
            case PolicyRule.QualityThreshold c -> new ConfigView(
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(c.metric().name()),
                    Optional.of(c.comparison().name()), Optional.of(c.threshold()), Optional.empty(), Optional.empty(), Optional.empty());
            case PolicyRule.QueryAssertion c -> new ConfigView(
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(c.comparison().name()),
                    Optional.empty(), Optional.of(c.expectedCount()), Optional.of(QueryPublicViews.query(c.query())), Optional.empty());
        };
    }

    private static RuleResultView ruleResult(PolicyEvaluation.RuleResult value) {
        return new RuleResultView(
                value.ruleId().toString(), value.kind().name(), value.severity().name(), value.applicability().name(),
                value.originalDecision().name(), value.effectiveDecision().name(), value.reason(),
                value.appliedOverride().map(PolicyPublicViews::override),
                value.observedValue().isPresent() ? Optional.of(value.observedValue().getAsDouble()) : Optional.empty(),
                value.evidence());
    }

    private static ScopeView scope(PolicyScope value) {
        return new ScopeView(value.type(), value.identity());
    }

    public record ScopeView(String kind, String id) {}
    public record DefinitionView(String id, String name, long revision, long latestVersionNumber, String createdAt, String updatedAt) {}
    public record VersionView(String packId, String versionId, long versionNumber, String name, List<RuleView> rules, String createdAt) {}
    public record RuleView(String id, String description, String kind, String severity, ConfigView config) {}
    public record ConfigView(
            Optional<String> changeId,
            Optional<String> sourceState,
            Optional<String> targetState,
            Optional<String> qualityMetric,
            Optional<String> comparison,
            Optional<Double> threshold,
            Optional<Long> expectedCount,
            Optional<QueryPublicViews.QueryDefinitionView> query,
            Optional<String> reserved) {}
    public record ActivationView(ScopeView scope, String packId, String versionId, long revision, String actor, String updatedAt) {}
    public record OverrideView(ScopeView scope, String packId, String ruleId, String mode, String reason, String actor, long revision, String updatedAt) {}
    public record AuditView(
            String id, String action, String packId, Optional<String> versionId, Optional<String> ruleId,
            Optional<ScopeView> scope, String actor, String reason, String at) {}
    public record RuleResultView(
            String ruleId, String kind, String severity, String applicability, String originalDecision,
            String effectiveDecision, String reason, Optional<OverrideView> appliedOverride,
            Optional<Double> observedValue, List<String> evidence) {}
    public record ReportView(
            ScopeView scope, String packId, String versionId, long versionNumber,
            boolean dryRun, String decision, List<RuleResultView> rules) {}
    public record GovernanceReportView(ScopeView scope, String decision, List<ReportView> packs) {}
}