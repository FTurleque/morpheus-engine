package com.morpheus.application.query.dsl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The closed field registry of the query DSL: every entity family has a schema, in a stable order that is also the
 * default projection, and the operators a field admits follow its type alone.
 */
class QuerySchemaRegistryTest {
    private static final Set<QueryOperator> SCALAR = Set.of(
            QueryOperator.EQ, QueryOperator.NEQ, QueryOperator.IN, QueryOperator.EXISTS);
    private static final Set<QueryOperator> TEXT = Set.of(
            QueryOperator.EQ, QueryOperator.NEQ, QueryOperator.CONTAINS,
            QueryOperator.STARTS_WITH, QueryOperator.ENDS_WITH, QueryOperator.IN, QueryOperator.EXISTS);

    @Test
    void everyEntityFamilyHasASchemaWhoseNamesAreItsKeysAndItsDefaultProjection() {
        for (QueryEntityType type : QueryEntityType.values()) {
            Map<String, QueryFieldDefinition> fields = QuerySchemaRegistry.fields(type);

            assertFalse(fields.isEmpty(), type + " has no field");
            fields.forEach((name, field) -> assertEquals(name, field.name(), type + " keys " + name));
            assertEquals(List.copyOf(fields.keySet()), QuerySchemaRegistry.defaultProjection(type),
                    type + " projects its fields in the order of its schema");
        }
    }

    @Test
    void theOrderOfASchemaIsTheOrderItDeclaresNotAHashOrder() {
        assertEquals(List.of("id", "projectId", "specificationId", "key", "title", "statement", "providerId"),
                QuerySchemaRegistry.defaultProjection(QueryEntityType.REQUIREMENT));
        assertEquals(List.of("id", "projectId", "changeId", "statement", "applicability", "severity", "satisfaction",
                        "blockingMode", "providerId"),
                QuerySchemaRegistry.defaultProjection(QueryEntityType.CONSTRAINT));
        assertEquals(List.of("portfolioId", "projectId", "displayName", "workspace", "repository", "providers",
                        "status"),
                QuerySchemaRegistry.defaultProjection(QueryEntityType.PORTFOLIO_MEMBERSHIP));
    }

    @Test
    void theOperatorsOfAFieldFollowItsTypeAndOnlyAnIdentityIsMarkedAsOne() {
        for (QueryEntityType type : QueryEntityType.values()) {
            for (QueryFieldDefinition field : QuerySchemaRegistry.fields(type).values()) {
                String where = type + "." + field.name();
                assertEquals(field.type() == QueryFieldType.TEXT ? TEXT : SCALAR, field.operators(), where);
                assertEquals(field.type() == QueryFieldType.IDENTITY, field.identity(), where);
            }
        }
        Map<String, QueryFieldDefinition> task = QuerySchemaRegistry.fields(QueryEntityType.TASK);
        assertEquals(QueryFieldType.BOOLEAN, task.get("completed").type());
        assertEquals(QueryFieldType.ENUM,
                QuerySchemaRegistry.fields(QueryEntityType.ACCEPTANCE_CRITERION).get("verificationStatus").type());
        assertTrue(QuerySchemaRegistry.fields(QueryEntityType.EVIDENCE).keySet().stream().noneMatch("providerId"::equals),
                "evidence carries no provider identity");
    }

    @Test
    void aSchemaAndAProjectionCannotBeChangedByTheirCaller() {
        Map<String, QueryFieldDefinition> fields = QuerySchemaRegistry.fields(QueryEntityType.TASK);
        List<String> projection = QuerySchemaRegistry.defaultProjection(QueryEntityType.TASK);

        UnsupportedOperationException schemaRefusal = assertThrows(UnsupportedOperationException.class,
                () -> fields.remove("title"));
        UnsupportedOperationException projectionRefusal = assertThrows(UnsupportedOperationException.class,
                () -> projection.add("extra"));

        assertTrue(schemaRefusal.getStackTrace()[0].getClassName().startsWith("java.util.Collections"),
                () -> "the schema refuses through its unmodifiable view: " + schemaRefusal.getStackTrace()[0]);
        assertTrue(projectionRefusal.getStackTrace()[0].getClassName().startsWith("java.util.ImmutableCollections"),
                () -> "the projection is an immutable copy: " + projectionRefusal.getStackTrace()[0]);
        assertEquals(QuerySchemaRegistry.defaultProjection(QueryEntityType.TASK), projection);
    }
}
