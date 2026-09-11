package com.morpheus.application.query.dsl;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryRowOperationsTest {
    private final QueryRowOperations operations = new QueryRowOperations();

    @Test
    void evaluatesEveryPredicateAndBooleanOperator() {
        QueryRow row = changeRow("project-b", "chg-2", "Alpha Billing", List.of("billing", "api"));

        assertTrue(operations.matches(row, QueryPredicate.unary("title", QueryOperator.EQ, "alpha billing")));
        assertTrue(operations.matches(row, QueryPredicate.unary("title", QueryOperator.NEQ, "other")));
        assertTrue(operations.matches(row, QueryPredicate.unary("title", QueryOperator.CONTAINS, "BILL")));
        assertTrue(operations.matches(row, QueryPredicate.unary("title", QueryOperator.STARTS_WITH, "alpha")));
        assertTrue(operations.matches(row, QueryPredicate.unary("title", QueryOperator.ENDS_WITH, "LING")));
        assertTrue(operations.matches(row, new QueryPredicate("title", QueryOperator.IN, List.of("other", "Alpha Billing"))));
        assertTrue(operations.matches(row, QueryPredicate.exists("scope")));
        assertFalse(operations.matches(row, QueryPredicate.exists("key")));

        QueryFilter title = QueryPredicate.unary("title", QueryOperator.CONTAINS, "billing");
        QueryFilter scope = QueryPredicate.unary("scope", QueryOperator.EQ, "api");
        QueryFilter missing = QueryPredicate.unary("key", QueryOperator.EQ, "CHG-404");
        assertTrue(operations.matches(row, new QueryAnd(List.of(title, scope))));
        assertFalse(operations.matches(row, new QueryAnd(List.of(title, missing))));
        assertTrue(operations.matches(row, new QueryOr(List.of(missing, scope))));
        assertFalse(operations.matches(row, new QueryOr(List.of(missing, QueryPredicate.unary("title", QueryOperator.EQ, "nope")))));
        assertTrue(operations.matches(row, new QueryNot(missing)));
        assertFalse(operations.matches(row, new QueryNot(title)));
    }

    @Test
    void comparatorHonoursDirectionThenStableProjectAndEntityTies() {
        QueryRow alphaB = requirementRow("project-b", "req-2", "Alpha");
        QueryRow alphaA2 = requirementRow("project-a", "req-2", "Alpha");
        QueryRow alphaA1 = requirementRow("project-a", "req-1", "Alpha");
        QueryRow zulu = requirementRow("project-a", "req-9", "Zulu");

        var ascending = operations.comparator(query(
                QueryEntityType.REQUIREMENT,
                List.of(new QuerySort("title", QuerySortDirection.ASC)),
                QueryProjection.defaults()));
        assertEquals(List.of(alphaA1, alphaA2, alphaB, zulu),
                List.of(zulu, alphaB, alphaA2, alphaA1).stream().sorted(ascending).toList());

        var descending = operations.comparator(query(
                QueryEntityType.REQUIREMENT,
                List.of(new QuerySort("title", QuerySortDirection.DESC)),
                QueryProjection.defaults()));
        assertEquals(zulu, List.of(alphaB, zulu).stream().sorted(descending).findFirst().orElseThrow());
    }

    @Test
    void columnsKeepIdentityFieldsAndSupportDefaultAndExplicitProjection() {
        List<String> requirementDefaults = operations.columns(query(
                QueryEntityType.REQUIREMENT, List.of(), QueryProjection.defaults()));
        assertEquals("id", requirementDefaults.get(0));
        assertEquals("projectId", requirementDefaults.get(1));
        assertTrue(requirementDefaults.contains("statement"));

        assertEquals(
                List.of("id", "projectId", "title"),
                operations.columns(query(
                        QueryEntityType.REQUIREMENT,
                        List.of(),
                        new QueryProjection(List.of("title")))));
        assertEquals(
                List.of("portfolioId", "projectId", "displayName"),
                operations.columns(query(
                        QueryEntityType.PORTFOLIO_MEMBERSHIP,
                        List.of(),
                        new QueryProjection(List.of("displayName")))));
        assertEquals(
                List.of("id", "portfolioId", "projectId", "relation"),
                operations.columns(query(
                        QueryEntityType.PORTFOLIO_REFERENCE,
                        List.of(),
                        new QueryProjection(List.of("relation")))));
    }

    private QueryDefinition query(QueryEntityType type, List<QuerySort> sort, QueryProjection projection) {
        return new QueryDefinition(
                new ProjectQueryScope(ProjectSpecificationId.generate()),
                type,
                Optional.empty(),
                sort,
                projection,
                QueryPage.first(10));
    }

    private QueryRow changeRow(String projectId, String entityId, String title, List<String> scope) {
        return new QueryRow(QueryEntityType.CHANGE, projectId, entityId, List.of(
                QueryCell.scalar("id", entityId),
                QueryCell.scalar("projectId", projectId),
                QueryCell.scalar("title", title),
                new QueryCell("scope", scope)));
    }

    private QueryRow requirementRow(String projectId, String entityId, String title) {
        return new QueryRow(QueryEntityType.REQUIREMENT, projectId, entityId, List.of(
                QueryCell.scalar("id", entityId),
                QueryCell.scalar("projectId", projectId),
                QueryCell.scalar("title", title)));
    }
}
