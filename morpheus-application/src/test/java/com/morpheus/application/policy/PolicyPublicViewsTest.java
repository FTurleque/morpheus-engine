package com.morpheus.application.policy;

import com.morpheus.application.query.PageRequest;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The policy projections every transport shares: identities as text, enums by name, absences as empty. */
class PolicyPublicViewsTest {
    private static final Instant AT = Instant.parse("2026-10-30T08:00:00Z");

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final PolicyScope scope = new PolicyScope.Project(projectId);
    private final PolicyIds.PackId packId = PolicyIds.PackId.generate();
    private final PolicyIds.VersionId versionId = PolicyIds.VersionId.generate();
    private final PolicyIds.RuleId ruleId = PolicyIds.RuleId.generate();

    @Test
    void anActivationAndAnOverrideNameTheirScopeAndIdentities() {
        PolicyConfiguration.Activation activation = new PolicyConfiguration.Activation(
                scope, packId, versionId, 2, "operator", AT);
        PolicyConfiguration.Override override = override();

        PolicyPublicViews.ActivationView activationView = PolicyPublicViews.activations(List.of(activation)).getFirst();
        PolicyPublicViews.OverrideView overrideView = PolicyPublicViews.overrides(List.of(override)).getFirst();

        assertEquals(new PolicyPublicViews.ActivationView(
                new PolicyPublicViews.ScopeView("PROJECT", projectId.toString()), packId.toString(),
                versionId.toString(), 2, "operator", AT.toString()), activationView);
        assertEquals(new PolicyPublicViews.OverrideView(
                new PolicyPublicViews.ScopeView("PROJECT", projectId.toString()), packId.toString(),
                ruleId.toString(), "FORCE_WARN", "accepted risk", "operator", 1, AT.toString()), overrideView);
    }

    @Test
    void anAuditPageCarriesItsBoundsAndEachRecordItsOptionalParts() {
        PortfolioId portfolioId = PortfolioId.generate();
        PolicyConfiguration.AuditRecord full = new PolicyConfiguration.AuditRecord(
                DomainIdentity.generate(), PolicyConfiguration.AuditAction.PUT_OVERRIDE, packId, Optional.of(versionId),
                Optional.of(ruleId), Optional.of(new PolicyScope.Portfolio(portfolioId)), "operator", "why", AT);
        PolicyConfiguration.AuditRecord bare = new PolicyConfiguration.AuditRecord(
                DomainIdentity.generate(), PolicyConfiguration.AuditAction.CREATE, packId, Optional.empty(),
                Optional.empty(), Optional.empty(), "operator", "why", AT.plusSeconds(1));

        PolicyPublicViews.AuditPageView page = PolicyPublicViews.auditPage(
                PolicyAuditPage.of(List.of(full, bare), new PageRequest(0, 1)));

        assertEquals(0, page.offset());
        assertEquals(1, page.limit());
        assertEquals(2, page.totalMatches());
        assertEquals(true, page.hasMore());
        PolicyPublicViews.AuditView view = page.items().getFirst();
        assertEquals("PUT_OVERRIDE", view.action());
        assertEquals(Optional.of(versionId.toString()), view.versionId());
        assertEquals(Optional.of(ruleId.toString()), view.ruleId());
        assertEquals(Optional.of(new PolicyPublicViews.ScopeView("PORTFOLIO", portfolioId.toString())), view.scope());
        PolicyPublicViews.AuditView bareView = PolicyPublicViews.audit(List.of(bare)).getFirst();
        assertEquals(Optional.empty(), bareView.versionId());
        assertEquals(Optional.empty(), bareView.scope());
    }

    @Test
    void aGovernanceReportProjectsEveryPackAndEveryRuleResult() {
        PolicyEvaluation.RuleResult measured = new PolicyEvaluation.RuleResult(
                ruleId, PolicyRule.Kind.QUALITY_THRESHOLD, PolicyRule.Severity.WARNING,
                PolicyEvaluation.Applicability.APPLICABLE, PolicyEvaluation.Decision.WARN,
                PolicyEvaluation.Decision.WARN, "measured", Optional.of(override()), OptionalDouble.of(4.5),
                List.of("evidence"));
        PolicyEvaluation.RuleResult unmeasured = new PolicyEvaluation.RuleResult(
                PolicyIds.RuleId.generate(), PolicyRule.Kind.CONSTRAINT_GUARD, PolicyRule.Severity.BLOCKER,
                PolicyEvaluation.Applicability.UNKNOWN, PolicyEvaluation.Decision.UNKNOWN,
                PolicyEvaluation.Decision.UNKNOWN, "unseen", Optional.empty(), OptionalDouble.empty(), List.of());
        PolicyEvaluation.Report report = new PolicyEvaluation.Report(
                scope, packId, versionId, 3, true, PolicyEvaluation.Decision.UNKNOWN, List.of(measured, unmeasured));

        PolicyPublicViews.GovernanceReportView view = PolicyPublicViews.governance(
                new PolicyEvaluation.GovernanceReport(scope, List.of(report), PolicyEvaluation.Decision.UNKNOWN));

        assertEquals("UNKNOWN", view.decision());
        PolicyPublicViews.ReportView pack = view.packs().getFirst();
        assertEquals(packId.toString(), pack.packId());
        assertEquals(3, pack.versionNumber());
        assertEquals(true, pack.dryRun());
        PolicyPublicViews.RuleResultView first = pack.rules().stream()
                .filter(rule -> rule.ruleId().equals(ruleId.toString())).findFirst().orElseThrow();
        assertEquals(Optional.of(4.5), first.observedValue());
        assertEquals("FORCE_WARN", first.appliedOverride().orElseThrow().mode());
        assertEquals(List.of("evidence"), first.evidence());
        PolicyPublicViews.RuleResultView second = pack.rules().stream()
                .filter(rule -> !rule.ruleId().equals(ruleId.toString())).findFirst().orElseThrow();
        assertEquals(Optional.empty(), second.observedValue());
        assertEquals("UNKNOWN", second.applicability());
    }

    private PolicyConfiguration.Override override() {
        return new PolicyConfiguration.Override(
                scope, packId, ruleId, PolicyConfiguration.OverrideMode.FORCE_WARN, "accepted risk", "operator", 1, AT);
    }
}
