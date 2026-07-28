package com.morpheus.application.query.dsl;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Closed provider-neutral field registry shared by validation, execution and exports. */
public final class QuerySchemaRegistry {
    private static final Set<QueryOperator> TEXT = Set.of(
            QueryOperator.EQ, QueryOperator.NEQ, QueryOperator.CONTAINS,
            QueryOperator.STARTS_WITH, QueryOperator.ENDS_WITH, QueryOperator.IN, QueryOperator.EXISTS);
    private static final Set<QueryOperator> SCALAR = Set.of(
            QueryOperator.EQ, QueryOperator.NEQ, QueryOperator.IN, QueryOperator.EXISTS);

    private static final Map<QueryEntityType, Map<String, QueryFieldDefinition>> SCHEMAS = schemas();

    private QuerySchemaRegistry() {
    }

    public static Map<String, QueryFieldDefinition> fields(QueryEntityType type) {
        Map<String, QueryFieldDefinition> schema = SCHEMAS.get(type);
        if (schema == null) {
            throw new IllegalArgumentException("unsupported query entity type: " + type);
        }
        return schema;
    }

    public static List<String> defaultProjection(QueryEntityType type) {
        return List.copyOf(fields(type).keySet());
    }

    private static Map<QueryEntityType, Map<String, QueryFieldDefinition>> schemas() {
        EnumMap<QueryEntityType, Map<String, QueryFieldDefinition>> result = new EnumMap<>(QueryEntityType.class);
        result.put(QueryEntityType.REQUIREMENT, schema(
                id("id"), id("projectId"), id("specificationId"), text("key"), text("title"), text("statement"), id("providerId")));
        result.put(QueryEntityType.SPECIFICATION, schema(
                id("id"), id("projectId"), text("key"), text("title"), text("description"), id("providerId")));
        result.put(QueryEntityType.SCENARIO, schema(
                id("id"), id("projectId"), id("requirementId"), text("title"), text("preconditions"),
                text("action"), text("expectedOutcome"), id("providerId")));
        result.put(QueryEntityType.CHANGE, schema(
                id("id"), id("projectId"), text("key"), text("title"), text("intent"), text("scope"),
                text("outOfScope"), text("risks"), id("providerId")));
        result.put(QueryEntityType.CONSTRAINT, schema(
                id("id"), id("projectId"), id("changeId"), text("statement"), enumeration("applicability"),
                enumeration("severity"), enumeration("satisfaction"), enumeration("blockingMode"), id("providerId")));
        result.put(QueryEntityType.DESIGN_DECISION, schema(
                id("id"), id("projectId"), id("changeId"), text("title"), text("decision"), id("providerId")));
        result.put(QueryEntityType.TASK, schema(
                id("id"), id("projectId"), id("changeId"), text("key"), text("title"), bool("completed"), id("providerId")));
        result.put(QueryEntityType.ACCEPTANCE_CRITERION, schema(
                id("id"), id("projectId"), id("requirementId"), id("changeId"), text("title"), text("condition"),
                enumeration("verificationStatus"), id("providerId")));
        result.put(QueryEntityType.EVIDENCE, schema(
                id("id"), id("projectId"), text("source"), text("range"), text("excerptHash")));
        result.put(QueryEntityType.PORTFOLIO_MEMBERSHIP, schema(
                id("portfolioId"), id("projectId"), text("displayName"), text("workspace"), text("repository"),
                text("providers"), enumeration("status")));
        result.put(QueryEntityType.PORTFOLIO_REFERENCE, schema(
                id("id"), id("portfolioId"), id("projectId"), id("sourceProjectId"), text("sourceType"),
                id("sourceId"), id("targetProjectId"), text("targetType"), id("targetId"), text("relation"),
                id("providerId"), text("sourceLocator"), id("evidenceId")));
        return Map.copyOf(result);
    }

    private static Map<String, QueryFieldDefinition> schema(QueryFieldDefinition... fields) {
        LinkedHashMap<String, QueryFieldDefinition> result = new LinkedHashMap<>();
        for (QueryFieldDefinition field : fields) {
            if (result.putIfAbsent(field.name(), field) != null) {
                throw new IllegalStateException("duplicate query field: " + field.name());
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static QueryFieldDefinition id(String name) {
        return new QueryFieldDefinition(name, QueryFieldType.IDENTITY, SCALAR, true);
    }

    private static QueryFieldDefinition text(String name) {
        return new QueryFieldDefinition(name, QueryFieldType.TEXT, TEXT, false);
    }

    private static QueryFieldDefinition enumeration(String name) {
        return new QueryFieldDefinition(name, QueryFieldType.ENUM, SCALAR, false);
    }

    private static QueryFieldDefinition bool(String name) {
        return new QueryFieldDefinition(name, QueryFieldType.BOOLEAN, SCALAR, false);
    }
}
