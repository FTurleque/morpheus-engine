package com.morpheus.cli;

import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.policy.DefaultPolicyFactResolver;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyEvaluationService;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyPublicViews;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.query.ConstraintEvaluationQueryService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.dsl.QueryDefinitionCodec;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqlitePolicyPackStore;
import com.morpheus.store.sqlite.SqlitePortfolioStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** M25 CLI adapter. Policy semantics remain centralized in application services. */
final class MorpheusPolicyCli {
    private static final String OPT_ACTOR = "actor";
    private static final String OPT_REASON = "reason";
    private static final String OPT_RULES = "rules";
    private static final String OPT_EXPECTED_REVISION = "expected-revision";
    private static final String OPT_PROJECT = "project";
    private static final String OPT_PORTFOLIO = "portfolio";
    private static final String OPT_VERSION = "version";

    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final QueryDefinitionCodec queryCodec = new QueryDefinitionCodec();

    static boolean handles(String[] args) {
        return command(args).equals("policy");
    }

    int run(String[] args, PrintStream out, PrintStream err, Map<String, String> environment, Properties properties) {
        try {
            Parsed parsed = Parsed.parse(args, environment, properties);
            try (Runtime runtime = new Runtime(parsed.layout().databasePath())) {
                Object result = execute(parsed, runtime);
                if (result != VoidMarker.INSTANCE) {
                    out.println(parsed.json() ? json.toJson(result) : result);
                }
                return CliExitCode.SUCCESS.code();
            }
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS error [" + CliExitCode.USAGE.code() + "]: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS error [" + CliExitCode.STATE_ERROR.code() + "]: " + safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    private Object execute(Parsed parsed, Runtime runtime) {
        SimpleOptions options = SimpleOptions.parse(parsed.arguments());
        return switch (parsed.action()) {
            case "pack-create" -> {
                options.rejectUnknown(Set.of("name", OPT_RULES, OPT_ACTOR, OPT_REASON));
                yield PolicyPublicViews.definition(runtime.registry.create(
                        options.required("name"), rules(options.required(OPT_RULES)),
                        options.required(OPT_ACTOR), options.required(OPT_REASON)));
            }
            case "pack-list" -> {
                options.rejectUnknown(Set.of());
                yield PolicyPublicViews.definitions(runtime.registry.list());
            }
            case "pack-get" -> {
                options.rejectUnknown(Set.of("id"));
                yield PolicyPublicViews.definition(runtime.registry.get(pack(options)));
            }
            case "pack-versions" -> {
                options.rejectUnknown(Set.of("id"));
                yield PolicyPublicViews.versions(runtime.registry.versions(pack(options)));
            }
            case "pack-update" -> {
                options.rejectUnknown(Set.of("id", OPT_EXPECTED_REVISION, "name", OPT_RULES, OPT_ACTOR, OPT_REASON));
                yield PolicyPublicViews.definition(runtime.registry.update(
                        pack(options), revision(options), options.required("name"), rules(options.required(OPT_RULES)),
                        options.required(OPT_ACTOR), options.required(OPT_REASON)));
            }
            case "activate" -> {
                options.rejectUnknown(Set.of("id", OPT_VERSION, OPT_PROJECT, OPT_PORTFOLIO, OPT_EXPECTED_REVISION, OPT_ACTOR, OPT_REASON));
                yield PolicyPublicViews.activation(runtime.registry.activate(
                        scope(options), pack(options), PolicyIds.VersionId.parse(options.required(OPT_VERSION)), revisionAllowZero(options),
                        options.required(OPT_ACTOR), options.required(OPT_REASON)));
            }
            case "activations" -> {
                options.rejectUnknown(Set.of(OPT_PROJECT, OPT_PORTFOLIO));
                yield PolicyPublicViews.activations(runtime.registry.activations(scope(options)));
            }
            case "deactivate" -> {
                options.rejectUnknown(Set.of("id", OPT_PROJECT, OPT_PORTFOLIO, OPT_EXPECTED_REVISION, OPT_ACTOR, OPT_REASON));
                runtime.registry.deactivate(scope(options), pack(options), revision(options),
                        options.required(OPT_ACTOR), options.required(OPT_REASON));
                yield VoidMarker.INSTANCE;
            }
            case "override-put" -> {
                options.rejectUnknown(Set.of("id", "rule", "mode", OPT_PROJECT, OPT_PORTFOLIO, OPT_EXPECTED_REVISION, OPT_ACTOR, OPT_REASON));
                yield PolicyPublicViews.override(runtime.registry.putOverride(
                        scope(options), pack(options), PolicyIds.RuleId.parse(options.required("rule")),
                        PolicyConfiguration.OverrideMode.valueOf(options.required("mode").toUpperCase(Locale.ROOT)),
                        revisionAllowZero(options), options.required(OPT_ACTOR), options.required(OPT_REASON)));
            }
            case "override-list" -> {
                options.rejectUnknown(Set.of(OPT_PROJECT, OPT_PORTFOLIO));
                yield PolicyPublicViews.overrides(runtime.registry.overrides(scope(options)));
            }
            case "override-remove" -> {
                options.rejectUnknown(Set.of("id", "rule", OPT_PROJECT, OPT_PORTFOLIO, OPT_EXPECTED_REVISION, OPT_ACTOR, OPT_REASON));
                runtime.registry.removeOverride(
                        scope(options), pack(options), PolicyIds.RuleId.parse(options.required("rule")), revision(options),
                        options.required(OPT_ACTOR), options.required(OPT_REASON));
                yield Map.of("removed", true);
            }
            case "evaluate" -> {
                options.rejectUnknown(Set.of("id", OPT_PROJECT, OPT_PORTFOLIO));
                PolicyScope evaluationScope = scope(options);
                Optional<String> packId = options.optional("id");
                if (packId.isPresent()) {
                    yield PolicyPublicViews.report(runtime.evaluation.evaluatePack(
                            evaluationScope, PolicyIds.PackId.parse(packId.orElseThrow())));
                }
                yield PolicyPublicViews.governance(runtime.evaluation.evaluate(evaluationScope));
            }
            case "dry-run" -> {
                options.rejectUnknown(Set.of("id", OPT_VERSION, OPT_PROJECT, OPT_PORTFOLIO));
                yield PolicyPublicViews.report(runtime.evaluation.dryRun(
                        scope(options), pack(options), PolicyIds.VersionId.parse(options.required(OPT_VERSION))));
            }
            case "audit" -> {
                options.rejectUnknown(Set.of("id"));
                yield PolicyPublicViews.audit(runtime.registry.audit(pack(options)));
            }
            default -> throw new IllegalArgumentException("unknown policy action: " + parsed.action());
        };
    }

    /**
     * Rules grammar, separated by ';;':
     * id-or-new|description|KIND|SEVERITY|kind-specific fields.
     * QUERY_ASSERTION receives an encoded QueryDefinition from QueryDefinitionCodec.
     */
    private List<PolicyRule> rules(String raw) {
        List<PolicyRule> result = new ArrayList<>();
        for (String entry : raw.split(";;", -1)) {
            String[] fields = entry.split("\\|", -1);
            if (fields.length < 4) {
                throw new IllegalArgumentException("invalid policy rule: expected id|description|kind|severity|...");
            }
            PolicyIds.RuleId id = fields[0].equalsIgnoreCase("new")
                    ? PolicyIds.RuleId.generate() : PolicyIds.RuleId.parse(fields[0]);
            String description = fields[1];
            PolicyRule.Kind kind = PolicyRule.Kind.valueOf(fields[2].toUpperCase(Locale.ROOT));
            PolicyRule.Severity severity = PolicyRule.Severity.valueOf(fields[3].toUpperCase(Locale.ROOT));
            PolicyRule.Config config = switch (kind) {
                case CONSTRAINT_GUARD -> {
                    requireFields(fields, 6, kind);
                    yield new PolicyRule.ConstraintGuard(ChangeId.parse(fields[4]), ChangeLifecycleState.valueOf(fields[5].toUpperCase(Locale.ROOT)));
                }
                case LIFECYCLE_GUARD -> {
                    requireFields(fields, 7, kind);
                    yield new PolicyRule.LifecycleGuard(
                            ChangeId.parse(fields[4]), ChangeLifecycleState.valueOf(fields[5].toUpperCase(Locale.ROOT)),
                            ChangeLifecycleState.valueOf(fields[6].toUpperCase(Locale.ROOT)));
                }
                case QUALITY_THRESHOLD -> {
                    requireFields(fields, 7, kind);
                    yield new PolicyRule.QualityThreshold(
                            PolicyRule.QualityMetric.valueOf(fields[4].toUpperCase(Locale.ROOT)),
                            PolicyRule.Comparison.valueOf(fields[5].toUpperCase(Locale.ROOT)), Double.parseDouble(fields[6]));
                }
                case QUERY_ASSERTION -> {
                    requireFields(fields, 7, kind);
                    yield new PolicyRule.QueryAssertion(
                            queryCodec.decode(fields[4]), PolicyRule.Comparison.valueOf(fields[5].toUpperCase(Locale.ROOT)),
                            Long.parseLong(fields[6]));
                }
            };
            result.add(new PolicyRule(id, description, kind, severity, config));
        }
        return List.copyOf(result);
    }

    private void requireFields(String[] fields, int expected, PolicyRule.Kind kind) {
        if (fields.length != expected) {
            throw new IllegalArgumentException("invalid " + kind + " rule: expected " + expected + " pipe-separated fields");
        }
    }

    private PolicyIds.PackId pack(SimpleOptions options) {
        return PolicyIds.PackId.parse(options.required("id"));
    }

    private PolicyScope scope(SimpleOptions options) {
        Optional<String> project = options.optional(OPT_PROJECT);
        Optional<String> portfolio = options.optional(OPT_PORTFOLIO);
        if (project.isPresent() == portfolio.isPresent()) {
            throw new IllegalArgumentException("exactly one of --project or --portfolio is required");
        }
        return project.<PolicyScope>map(value -> new PolicyScope.Project(ProjectSpecificationId.parse(value)))
                .orElseGet(() -> new PolicyScope.Portfolio(PortfolioId.parse(portfolio.orElseThrow())));
    }

    private long revision(SimpleOptions options) {
        long value = parseLong(options.required(OPT_EXPECTED_REVISION), OPT_EXPECTED_REVISION);
        if (value <= 0) {
            throw new IllegalArgumentException("--expected-revision must be positive");
        }
        return value;
    }

    private long revisionAllowZero(SimpleOptions options) {
        long value = parseLong(options.required(OPT_EXPECTED_REVISION), OPT_EXPECTED_REVISION);
        if (value < 0) {
            throw new IllegalArgumentException("--expected-revision must be >= 0");
        }
        return value;
    }

    private long parseLong(String raw, String key) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("--" + key + " must be an integer", failure);
        }
    }

    private static String command(String[] args) {
        return GlobalArgs.command(args);
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private enum VoidMarker { INSTANCE }

    private record Parsed(boolean json, CliLayout layout, String action, List<String> arguments) {
        static Parsed parse(String[] args, Map<String, String> environment, Properties properties) {
            GlobalArgs.Parsed global = GlobalArgs.parse(args);
            List<String> remaining = global.remaining();
            if (remaining.isEmpty() || !remaining.getFirst().equals("policy") || remaining.size() < 2) {
                throw new IllegalArgumentException("policy action is required");
            }
            int consumed;
            String action;
            if ((remaining.get(1).equals("pack") || remaining.get(1).equals("override"))
                    && remaining.size() >= 3 && !remaining.get(2).startsWith("--")) {
                action = remaining.get(1) + "-" + remaining.get(2);
                consumed = 3;
            } else {
                action = remaining.get(1);
                consumed = 2;
            }
            return new Parsed(
                    global.json(),
                    CliLayout.resolve(global.dataDirectory(), global.configDirectory(), global.databasePath(),
                            environment, properties),
                    action, List.copyOf(remaining.subList(consumed, remaining.size())));
        }
    }

    private static final class Runtime implements AutoCloseable {
        private final SqliteSpecificationKnowledgeStore snapshots;
        private final SqliteVersionedRequirementStore requirements;
        private final SqliteSnapshotBusinessContentStore content;
        private final SqliteTraceabilityStore traceability;
        private final SqliteExternalReferenceStore externalReferences;
        private final SqlitePortfolioStore portfolios;
        private final SqlitePolicyPackStore policies;
        private final PolicyPackService registry;
        private final PolicyEvaluationService evaluation;

        Runtime(Path databasePath) {
            snapshots = new SqliteSpecificationKnowledgeStore(databasePath);
            requirements = new SqliteVersionedRequirementStore(databasePath);
            content = new SqliteSnapshotBusinessContentStore(databasePath);
            traceability = new SqliteTraceabilityStore(databasePath);
            externalReferences = new SqliteExternalReferenceStore(databasePath);
            portfolios = new SqlitePortfolioStore(databasePath);
            policies = new SqlitePolicyPackStore(databasePath);

            QueryExecutionService queries = new QueryExecutionService(snapshots, requirements, content, portfolios);
            ConstraintEvaluationQueryService constraints = new ConstraintEvaluationQueryService(snapshots, content);
            ChangeTransitionEvaluationService lifecycle = new ChangeTransitionEvaluationService(
                    snapshots, content, requirements, traceability);
            QualityReportService quality = new QualityReportService(
                    snapshots,
                    new RequirementQualityService(snapshots, requirements, traceability),
                    new TaskQualityService(snapshots, content, requirements, traceability),
                    new AcceptanceQualityService(snapshots, content),
                    new ChangeCompletenessService(snapshots, content, requirements, traceability),
                    new DecisionReferenceQualityService(snapshots, content, requirements, traceability, externalReferences));
            registry = new PolicyPackService(policies);
            evaluation = new PolicyEvaluationService(
                    policies, new DefaultPolicyFactResolver(constraints, lifecycle, quality, queries));
        }

        @Override
        public void close() {
            policies.close();
            portfolios.close();
            externalReferences.close();
            traceability.close();
            content.close();
            requirements.close();
            snapshots.close();
        }
    }
}
