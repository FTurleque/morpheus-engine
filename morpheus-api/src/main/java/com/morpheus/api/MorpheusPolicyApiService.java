package com.morpheus.api;

import com.morpheus.application.operability.ExhaustiveShutdown;
import com.morpheus.application.operability.StartupOwnership;
import com.morpheus.application.policy.PolicyBudgets;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyEvaluationService;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyPublicViews;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.policy.PolicyRuntimeServices;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.application.query.dsl.QueryDefinitionCodec;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.sqlite.SqlitePolicyStores;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** HTTP-facing M25 adapter: strict transport parsing and persistence wiring only. */
public final class MorpheusPolicyApiService {
    private final Path databasePath;
    private final QueryDefinitionCodec queryCodec = new QueryDefinitionCodec();

    public MorpheusPolicyApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    public Object create(CreateRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            return PolicyPublicViews.definition(runtime.registry.create(
                    requireText(request.name(), "name"), rules(request.rules()),
                    requireText(request.actor(), "actor"), requireText(request.reason(), "reason")));
        }
    }

    public Object list() {
        try (Runtime runtime = runtime()) {
            return PolicyPublicViews.definitions(runtime.registry.list());
        }
    }

    public Object get(String id) {
        try (Runtime runtime = runtime()) {
            return PolicyPublicViews.definition(runtime.registry.get(PolicyIds.PackId.parse(id)));
        }
    }

    public Object versions(String id) {
        try (Runtime runtime = runtime()) {
            return PolicyPublicViews.versions(runtime.registry.versions(PolicyIds.PackId.parse(id)));
        }
    }

    public Object update(String id, UpdateRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            return PolicyPublicViews.definition(runtime.registry.update(
                    PolicyIds.PackId.parse(id), positive(request.expectedRevision(), "expectedRevision"),
                    requireText(request.name(), "name"), rules(request.rules()),
                    requireText(request.actor(), "actor"), requireText(request.reason(), "reason")));
        }
    }

    public Object activate(String id, ActivationRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            return PolicyPublicViews.activation(runtime.registry.activate(
                    scope(request.scopeKind(), request.scopeId()), PolicyIds.PackId.parse(id),
                    PolicyIds.VersionId.parse(requireText(request.versionId(), "versionId")),
                    nonNegative(request.expectedRevision(), "expectedRevision"),
                    requireText(request.actor(), "actor"), requireText(request.reason(), "reason")));
        }
    }

    public Object deactivate(String id, DeactivationRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            runtime.registry.deactivate(
                    scope(request.scopeKind(), request.scopeId()), PolicyIds.PackId.parse(id),
                    positive(request.expectedRevision(), "expectedRevision"),
                    requireText(request.actor(), "actor"), requireText(request.reason(), "reason"));
            return new MutationView(true);
        }
    }

    public Object putOverride(String id, String ruleId, OverrideRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            return PolicyPublicViews.override(runtime.registry.putOverride(
                    scope(request.scopeKind(), request.scopeId()), PolicyIds.PackId.parse(id), PolicyIds.RuleId.parse(ruleId),
                    PolicyConfiguration.OverrideMode.valueOf(requireText(request.mode(), "mode").toUpperCase()),
                    nonNegative(request.expectedRevision(), "expectedRevision"),
                    requireText(request.actor(), "actor"), requireText(request.reason(), "reason")));
        }
    }

    public Object listOverrides(String scopeKind, String scopeId) {
        try (Runtime runtime = runtime()) {
            return PolicyPublicViews.overrides(runtime.registry.overrides(scope(scopeKind, scopeId)));
        }
    }

    public Object evaluate(EvaluateRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            PolicyScope scope = scope(request.scopeKind(), request.scopeId());
            if (request.id() == null || request.id().isBlank()) {
                return PolicyPublicViews.governance(runtime.evaluation.evaluate(scope));
            }
            return PolicyPublicViews.report(runtime.evaluation.evaluatePack(scope, PolicyIds.PackId.parse(request.id())));
        }
    }

    public Object dryRun(DryRunRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            return PolicyPublicViews.report(runtime.evaluation.dryRun(
                    scope(request.scopeKind(), request.scopeId()), PolicyIds.PackId.parse(requireText(request.id(), "id")),
                    PolicyIds.VersionId.parse(requireText(request.versionId(), "versionId"))));
        }
    }

    public Object audit(String id) {
        try (Runtime runtime = runtime()) {
            return PolicyPublicViews.audit(runtime.registry.audit(PolicyIds.PackId.parse(id)));
        }
    }

    private List<PolicyRule> rules(List<RuleRequest> requests) {
        Objects.requireNonNull(requests, "rules");
        if (requests.isEmpty() || requests.size() > PolicyBudgets.MAX_RULES_PER_PACK) {
            throw new IllegalArgumentException("rules must contain between 1 and " + PolicyBudgets.MAX_RULES_PER_PACK + " items");
        }
        List<PolicyRule> result = new ArrayList<>(requests.size());
        for (RuleRequest request : requests) {
            Objects.requireNonNull(request, "rules item");
            PolicyIds.RuleId id = request.id() == null || request.id().isBlank()
                    ? PolicyIds.RuleId.generate() : PolicyIds.RuleId.parse(request.id());
            PolicyRule.Kind kind = PolicyRule.Kind.valueOf(requireText(request.kind(), "rule.kind").toUpperCase());
            PolicyRule.Severity severity = PolicyRule.Severity.valueOf(requireText(request.severity(), "rule.severity").toUpperCase());
            PolicyRule.Config config = switch (kind) {
                case CONSTRAINT_GUARD -> new PolicyRule.ConstraintGuard(
                        ChangeId.parse(requireText(request.changeId(), "rule.changeId")),
                        ChangeLifecycleState.valueOf(requireText(request.targetState(), "rule.targetState").toUpperCase()));
                case LIFECYCLE_GUARD -> new PolicyRule.LifecycleGuard(
                        ChangeId.parse(requireText(request.changeId(), "rule.changeId")),
                        ChangeLifecycleState.valueOf(requireText(request.sourceState(), "rule.sourceState").toUpperCase()),
                        ChangeLifecycleState.valueOf(requireText(request.targetState(), "rule.targetState").toUpperCase()));
                case QUALITY_THRESHOLD -> new PolicyRule.QualityThreshold(
                        PolicyRule.QualityMetric.valueOf(requireText(request.qualityMetric(), "rule.qualityMetric").toUpperCase()),
                        PolicyRule.Comparison.valueOf(requireText(request.comparison(), "rule.comparison").toUpperCase()),
                        requireFinite(request.threshold(), "rule.threshold"));
                case QUERY_ASSERTION -> new PolicyRule.QueryAssertion(
                        queryCodec.decode(requireText(request.queryDefinition(), "rule.queryDefinition")),
                        PolicyRule.Comparison.valueOf(requireText(request.comparison(), "rule.comparison").toUpperCase()),
                        nonNegative(request.expectedCount(), "rule.expectedCount"));
            };
            result.add(new PolicyRule(id, requireText(request.description(), "rule.description"), kind, severity, config));
        }
        return List.copyOf(result);
    }

    private PolicyScope scope(String rawKind, String rawId) {
        String kind = requireText(rawKind, "scopeKind").toUpperCase();
        String id = requireText(rawId, "scopeId");
        return switch (kind) {
            case "PROJECT" -> new PolicyScope.Project(ProjectSpecificationId.parse(id));
            case "PORTFOLIO" -> new PolicyScope.Portfolio(PortfolioId.parse(id));
            default -> throw new IllegalArgumentException("scopeKind must be PROJECT or PORTFOLIO");
        };
    }

    private Runtime runtime() {
        return new Runtime(databasePath);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static long positive(Long value, String name) {
        if (value == null || value <= 0) throw new IllegalArgumentException(name + " must be a positive integer");
        return value;
    }

    private static long nonNegative(Long value, String name) {
        if (value == null || value < 0) throw new IllegalArgumentException(name + " must be >= 0");
        return value;
    }

    private static double requireFinite(Double value, String name) {
        if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }

    public record RuleRequest(
            String id, String description, String kind, String severity,
            String changeId, String sourceState, String targetState,
            String qualityMetric, String comparison, Double threshold,
            String queryDefinition, Long expectedCount) {}

    public record CreateRequest(String name, List<RuleRequest> rules, String actor, String reason) {}
    public record UpdateRequest(Long expectedRevision, String name, List<RuleRequest> rules, String actor, String reason) {}
    public record ActivationRequest(String versionId, String scopeKind, String scopeId, Long expectedRevision, String actor, String reason) {}
    public record DeactivationRequest(String scopeKind, String scopeId, Long expectedRevision, String actor, String reason) {}
    public record OverrideRequest(String scopeKind, String scopeId, String mode, Long expectedRevision, String actor, String reason) {}
    public record EvaluateRequest(String scopeKind, String scopeId, String id) {}
    public record DryRunRequest(String scopeKind, String scopeId, String id, String versionId) {}
    public record MutationView(boolean changed) {}

    private static final class Runtime implements AutoCloseable {
        private final SqlitePolicyStores stores;
        private final PolicyPackService registry;
        private final PolicyEvaluationService evaluation;

        private Runtime(Path databasePath) {
            try (StartupOwnership owned = new StartupOwnership()) {
                stores = SqlitePolicyStores.open(databasePath, owned);
                PolicyRuntimeServices services = PolicyRuntimeServices.from(
                        stores.snapshots(), stores.requirements(), stores.content(), stores.traceability(),
                        stores.externalReferences(), stores.portfolios(), stores.policies());
                registry = services.registry();
                evaluation = services.evaluation();

                owned.transferred();
            }
        }

        @Override
        public void close() {
            stores.close();
        }
    }
}