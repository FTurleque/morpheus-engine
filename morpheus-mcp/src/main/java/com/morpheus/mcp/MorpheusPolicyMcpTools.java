package com.morpheus.mcp;

import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.policy.DefaultPolicyFactResolver;
import com.morpheus.application.policy.PolicyBudgets;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyConflictException;
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
import com.morpheus.application.store.KnowledgeStoreException;
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
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Strict M25 MCP policy tools delegating all semantics to shared application services. */
final class MorpheusPolicyMcpTools {
    static final String CREATE = "create_policy_pack";
    static final String LIST = "list_policy_packs";
    static final String GET = "get_policy_pack";
    static final String VERSIONS = "list_policy_pack_versions";
    static final String UPDATE = "update_policy_pack";
    static final String ACTIVATE = "activate_policy_pack";
    static final String DEACTIVATE = "deactivate_policy_pack";
    static final String PUT_OVERRIDE = "put_policy_override";
    static final String LIST_OVERRIDES = "list_policy_overrides";
    static final String EVALUATE = "evaluate_policies";
    static final String DRY_RUN = "dry_run_policy_pack";
    static final String AUDIT = "get_policy_audit";

    private final Path databasePath;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final QueryDefinitionCodec queryCodec = new QueryDefinitionCodec();

    MorpheusPolicyMcpTools(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(CREATE, "Create one versioned provider-neutral policy pack.", createSchema()),
                tool(LIST, "List policy pack identities and current registry metadata.", emptySchema()),
                tool(GET, "Read one policy pack by stable identity.", idSchema()),
                tool(VERSIONS, "List immutable versions of one policy pack.", idSchema()),
                tool(UPDATE, "CAS-update a policy pack by creating a new immutable version.", updateSchema()),
                tool(ACTIVATE, "Explicitly activate one immutable policy version in a project or portfolio scope.", activationSchema(true)),
                tool(DEACTIVATE, "CAS-deactivate one policy pack from a scope.", activationSchema(false)),
                tool(PUT_OVERRIDE, "Create or CAS-update one explicit audited policy override.", overrideSchema()),
                tool(LIST_OVERRIDES, "List policy overrides for one explicit scope.", scopeSchema()),
                tool(EVALUATE, "Evaluate active policies read-only for one explicit scope.", evaluateSchema()),
                tool(DRY_RUN, "Dry-run one policy version without activation or mutation.", dryRunSchema()),
                tool(AUDIT, "Read immutable policy configuration audit records.", idSchema()));
    }

    private McpServerFeatures.SyncToolSpecification tool(String name, String description, Map<String, Object> schema) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, schema).description(description).build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(name, request.arguments()))
                .build();
    }

    private McpSchema.CallToolResult call(String toolName, Map<String, Object> rawArguments) {
        try {
            Map<String, Object> arguments = rawArguments == null ? Map.of() : rawArguments;
            try (Runtime runtime = new Runtime(databasePath)) {
                Object result = switch (toolName) {
                    case CREATE -> PolicyPublicViews.definition(runtime.registry.create(
                            requiredString(arguments, "name"), rules(arguments),
                            requiredString(arguments, "actor"), requiredString(arguments, "reason")));
                    case LIST -> PolicyPublicViews.definitions(runtime.registry.list());
                    case GET -> PolicyPublicViews.definition(runtime.registry.get(pack(arguments)));
                    case VERSIONS -> PolicyPublicViews.versions(runtime.registry.versions(pack(arguments)));
                    case UPDATE -> PolicyPublicViews.definition(runtime.registry.update(
                            pack(arguments), longValue(arguments, "expectedRevision", 1, Long.MAX_VALUE),
                            requiredString(arguments, "name"), rules(arguments),
                            requiredString(arguments, "actor"), requiredString(arguments, "reason")));
                    case ACTIVATE -> PolicyPublicViews.activation(runtime.registry.activate(
                            scope(arguments), pack(arguments), PolicyIds.VersionId.parse(requiredString(arguments, "versionId")),
                            longValue(arguments, "expectedRevision", 0, Long.MAX_VALUE),
                            requiredString(arguments, "actor"), requiredString(arguments, "reason")));
                    case DEACTIVATE -> {
                        runtime.registry.deactivate(
                                scope(arguments), pack(arguments), longValue(arguments, "expectedRevision", 1, Long.MAX_VALUE),
                                requiredString(arguments, "actor"), requiredString(arguments, "reason"));
                        yield Map.of("deactivated", true);
                    }
                    case PUT_OVERRIDE -> PolicyPublicViews.override(runtime.registry.putOverride(
                            scope(arguments), pack(arguments), PolicyIds.RuleId.parse(requiredString(arguments, "ruleId")),
                            PolicyConfiguration.OverrideMode.valueOf(requiredString(arguments, "mode").toUpperCase()),
                            longValue(arguments, "expectedRevision", 0, Long.MAX_VALUE),
                            requiredString(arguments, "actor"), requiredString(arguments, "reason")));
                    case LIST_OVERRIDES -> PolicyPublicViews.overrides(runtime.registry.overrides(scope(arguments)));
                    case EVALUATE -> {
                        PolicyScope evaluationScope = scope(arguments);
                        Optional<String> packId = optionalString(arguments, "id");
                        if (packId.isPresent()) {
                            yield PolicyPublicViews.report(runtime.evaluation.evaluatePack(
                                    evaluationScope, PolicyIds.PackId.parse(packId.orElseThrow())));
                        }
                        yield PolicyPublicViews.governance(runtime.evaluation.evaluate(evaluationScope));
                    }
                    case DRY_RUN -> PolicyPublicViews.report(runtime.evaluation.dryRun(
                            scope(arguments), pack(arguments), PolicyIds.VersionId.parse(requiredString(arguments, "versionId"))));
                    case AUDIT -> PolicyPublicViews.audit(runtime.registry.audit(pack(arguments)));
                    default -> throw new IllegalArgumentException("unknown M25 MCP tool: " + toolName);
                };
                return McpSchema.CallToolResult.builder()
                        .addTextContent(json.toJson(result))
                        .isError(false)
                        .build();
            }
        } catch (IllegalArgumentException | IllegalStateException | KnowledgeStoreException | PolicyConflictException expected) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(safeMessage(expected))
                    .isError(true)
                    .build();
        }
    }

    private List<PolicyRule> rules(Map<String, Object> arguments) {
        Object raw = arguments.get("rules");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("rules must be a non-empty array");
        }
        if (list.size() > PolicyBudgets.MAX_RULES_PER_PACK) {
            throw new IllegalArgumentException("rules exceeds maximum " + PolicyBudgets.MAX_RULES_PER_PACK);
        }
        List<PolicyRule> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rawRule)) {
                throw new IllegalArgumentException("rules items must be objects");
            }
            Map<String, Object> rule = stringKeyMap(rawRule);
            PolicyIds.RuleId id = optionalString(rule, "id").map(PolicyIds.RuleId::parse).orElseGet(PolicyIds.RuleId::generate);
            PolicyRule.Kind kind = PolicyRule.Kind.valueOf(requiredString(rule, "kind").toUpperCase());
            PolicyRule.Severity severity = PolicyRule.Severity.valueOf(requiredString(rule, "severity").toUpperCase());
            PolicyRule.Config config = switch (kind) {
                case CONSTRAINT_GUARD -> new PolicyRule.ConstraintGuard(
                        ChangeId.parse(requiredString(rule, "changeId")),
                        ChangeLifecycleState.valueOf(requiredString(rule, "targetState").toUpperCase()));
                case LIFECYCLE_GUARD -> new PolicyRule.LifecycleGuard(
                        ChangeId.parse(requiredString(rule, "changeId")),
                        ChangeLifecycleState.valueOf(requiredString(rule, "sourceState").toUpperCase()),
                        ChangeLifecycleState.valueOf(requiredString(rule, "targetState").toUpperCase()));
                case QUALITY_THRESHOLD -> new PolicyRule.QualityThreshold(
                        PolicyRule.QualityMetric.valueOf(requiredString(rule, "qualityMetric").toUpperCase()),
                        PolicyRule.Comparison.valueOf(requiredString(rule, "comparison").toUpperCase()),
                        doubleValue(rule, "threshold"));
                case QUERY_ASSERTION -> new PolicyRule.QueryAssertion(
                        queryCodec.decode(requiredString(rule, "queryDefinition")),
                        PolicyRule.Comparison.valueOf(requiredString(rule, "comparison").toUpperCase()),
                        longValue(rule, "expectedCount", 0, Long.MAX_VALUE));
            };
            result.add(new PolicyRule(id, requiredString(rule, "description"), kind, severity, config));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (!(key instanceof String text)) throw new IllegalArgumentException("rule property names must be strings");
            result.put(text, value);
        });
        return Map.copyOf(result);
    }

    private PolicyIds.PackId pack(Map<String, Object> arguments) {
        return PolicyIds.PackId.parse(requiredString(arguments, "id"));
    }

    private PolicyScope scope(Map<String, Object> arguments) {
        String kind = requiredString(arguments, "scopeKind").toUpperCase();
        String id = requiredString(arguments, "scopeId");
        return switch (kind) {
            case "PROJECT" -> new PolicyScope.Project(ProjectSpecificationId.parse(id));
            case "PORTFOLIO" -> new PolicyScope.Portfolio(PortfolioId.parse(id));
            default -> throw new IllegalArgumentException("scopeKind must be PROJECT or PORTFOLIO");
        };
    }

    private static Map<String, Object> createSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Map.of("type", "string", "minLength", 1, "maxLength", PolicyBudgets.MAX_PACK_NAME));
        properties.put("rules", rulesProperty());
        properties.put("actor", nonBlankString());
        properties.put("reason", nonBlankString());
        return schema(List.of("name", "rules", "actor", "reason"), properties);
    }

    private static Map<String, Object> updateSchema() {
        Map<String, Object> properties = new LinkedHashMap<>(createSchema().containsKey("properties")
                ? castMap(createSchema().get("properties")) : Map.of());
        properties.put("id", nonBlankString());
        properties.put("expectedRevision", Map.of("type", "integer", "minimum", 1));
        return schema(List.of("id", "expectedRevision", "name", "rules", "actor", "reason"), properties);
    }

    private static Map<String, Object> activationSchema(boolean version) {
        Map<String, Object> properties = new LinkedHashMap<>(scopeProperties());
        properties.put("id", nonBlankString());
        if (version) properties.put("versionId", nonBlankString());
        properties.put("expectedRevision", Map.of("type", "integer", "minimum", version ? 0 : 1));
        properties.put("actor", nonBlankString());
        properties.put("reason", nonBlankString());
        List<String> required = version
                ? List.of("id", "versionId", "scopeKind", "scopeId", "expectedRevision", "actor", "reason")
                : List.of("id", "scopeKind", "scopeId", "expectedRevision", "actor", "reason");
        return schema(required, properties);
    }

    private static Map<String, Object> overrideSchema() {
        Map<String, Object> properties = new LinkedHashMap<>(scopeProperties());
        properties.put("id", nonBlankString());
        properties.put("ruleId", nonBlankString());
        properties.put("mode", Map.of("type", "string", "enum", List.of("DISABLE", "FORCE_WARN", "FORCE_BLOCK")));
        properties.put("expectedRevision", Map.of("type", "integer", "minimum", 0));
        properties.put("actor", nonBlankString());
        properties.put("reason", nonBlankString());
        return schema(List.of("id", "ruleId", "mode", "scopeKind", "scopeId", "expectedRevision", "actor", "reason"), properties);
    }

    private static Map<String, Object> evaluateSchema() {
        Map<String, Object> properties = new LinkedHashMap<>(scopeProperties());
        properties.put("id", nonBlankString());
        return schema(List.of("scopeKind", "scopeId"), properties);
    }

    private static Map<String, Object> dryRunSchema() {
        Map<String, Object> properties = new LinkedHashMap<>(scopeProperties());
        properties.put("id", nonBlankString());
        properties.put("versionId", nonBlankString());
        return schema(List.of("id", "versionId", "scopeKind", "scopeId"), properties);
    }

    private static Map<String, Object> idSchema() {
        return schema(List.of("id"), Map.of("id", nonBlankString()));
    }

    private static Map<String, Object> scopeSchema() {
        return schema(List.of("scopeKind", "scopeId"), scopeProperties());
    }

    private static Map<String, Object> emptySchema() {
        return schema(List.of(), Map.of());
    }

    private static Map<String, Object> scopeProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("scopeKind", Map.of("type", "string", "enum", List.of("PROJECT", "PORTFOLIO")));
        properties.put("scopeId", nonBlankString());
        return properties;
    }

    private static Map<String, Object> rulesProperty() {
        Map<String, Object> ruleProperties = new LinkedHashMap<>();
        ruleProperties.put("id", nonBlankString());
        ruleProperties.put("description", Map.of("type", "string", "minLength", 1, "maxLength", PolicyBudgets.MAX_RULE_DESCRIPTION));
        ruleProperties.put("kind", Map.of("type", "string", "enum", List.of("CONSTRAINT_GUARD", "LIFECYCLE_GUARD", "QUALITY_THRESHOLD", "QUERY_ASSERTION")));
        ruleProperties.put("severity", Map.of("type", "string", "enum", List.of("INFO", "WARNING", "BLOCKER")));
        ruleProperties.put("changeId", nonBlankString());
        ruleProperties.put("sourceState", nonBlankString());
        ruleProperties.put("targetState", nonBlankString());
        ruleProperties.put("qualityMetric", nonBlankString());
        ruleProperties.put("comparison", Map.of("type", "string", "enum", List.of("EQ", "NE", "LT", "LTE", "GT", "GTE")));
        ruleProperties.put("threshold", Map.of("type", "number"));
        ruleProperties.put("queryDefinition", nonBlankString());
        ruleProperties.put("expectedCount", Map.of("type", "integer", "minimum", 0));
        Map<String, Object> item = schema(List.of("description", "kind", "severity"), ruleProperties);
        return Map.of("type", "array", "minItems", 1, "maxItems", PolicyBudgets.MAX_RULES_PER_PACK, "items", item);
    }

    private static Map<String, Object> schema(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> nonBlankString() {
        return Map.of("type", "string", "minLength", 1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static String requiredString(Map<String, Object> arguments, String key) {
        return optionalString(arguments, key)
                .orElseThrow(() -> new IllegalArgumentException(key + " must be a non-blank string"));
    }

    private static Optional<String> optionalString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must be a non-blank string when present");
        }
        return Optional.of(text.trim());
    }

    private static long longValue(Map<String, Object> arguments, String key, long minimum, long maximum) {
        Object raw = arguments.get(key);
        if (!(raw instanceof Number number)) throw new IllegalArgumentException(key + " must be an integer");
        long value = number.longValue();
        if (Double.compare(number.doubleValue(), (double) value) != 0 || value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be an integer between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static double doubleValue(Map<String, Object> arguments, String key) {
        Object raw = arguments.get(key);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(key + " must be a finite number");
        }
        return number.doubleValue();
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
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
            ChangeTransitionEvaluationService lifecycle = new ChangeTransitionEvaluationService(snapshots, content, requirements, traceability);
            QualityReportService quality = new QualityReportService(
                    snapshots,
                    new RequirementQualityService(snapshots, requirements, traceability),
                    new TaskQualityService(snapshots, content, requirements, traceability),
                    new AcceptanceQualityService(snapshots, content),
                    new ChangeCompletenessService(snapshots, content, requirements, traceability),
                    new DecisionReferenceQualityService(snapshots, content, requirements, traceability, externalReferences));
            registry = new PolicyPackService(policies);
            evaluation = new PolicyEvaluationService(policies, new DefaultPolicyFactResolver(constraints, lifecycle, quality, queries));
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
