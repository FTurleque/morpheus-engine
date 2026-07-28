package com.morpheus.architecture.m24;

import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryAnd;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryNot;
import com.morpheus.application.query.dsl.QueryOperator;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.application.query.dsl.QueryPredicate;
import com.morpheus.application.query.dsl.QueryProjection;
import com.morpheus.application.query.dsl.QuerySort;
import com.morpheus.application.query.dsl.QuerySortDirection;
import com.morpheus.application.query.dsl.QueryValidator;
import com.morpheus.application.query.dsl.QueryValidationException;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryDslContractTest {
    private static final ProjectSpecificationId PROJECT =
            ProjectSpecificationId.parse("01890f7a-36d4-7c1e-8000-000000000001");
    private final QueryValidator validator = new QueryValidator();

    @Test
    void acceptsBoundedProviderNeutralQuery() {
        QueryDefinition query = new QueryDefinition(
                new ProjectQueryScope(PROJECT),
                QueryEntityType.REQUIREMENT,
                Optional.of(new QueryAnd(List.of(
                        QueryPredicate.unary("title", QueryOperator.CONTAINS, "login"),
                        QueryPredicate.unary("providerId", QueryOperator.EQ, "openspec")))),
                List.of(new QuerySort("title", QuerySortDirection.ASC)),
                new QueryProjection(List.of("id", "title", "statement")),
                new QueryPage(0, 50));

        assertTrue(validator.validate(query).isEmpty());
    }

    @Test
    void rejectsUnknownFieldAndUnsupportedOperator() {
        QueryDefinition unknown = new QueryDefinition(
                new ProjectQueryScope(PROJECT), QueryEntityType.REQUIREMENT,
                Optional.of(QueryPredicate.unary("sqlite_table", QueryOperator.EQ, "requirements")),
                List.of(), QueryProjection.defaults(), QueryPage.first(10));
        QueryDefinition incompatible = new QueryDefinition(
                new ProjectQueryScope(PROJECT), QueryEntityType.TASK,
                Optional.of(QueryPredicate.unary("completed", QueryOperator.CONTAINS, "true")),
                List.of(), QueryProjection.defaults(), QueryPage.first(10));

        assertEquals("QUERY_FIELD_UNKNOWN", validator.validate(unknown).getFirst().code());
        assertEquals("QUERY_OPERATOR_INVALID", validator.validate(incompatible).getFirst().code());
    }

    @Test
    void rejectsInvalidTypedLiteral() {
        QueryDefinition query = new QueryDefinition(
                new ProjectQueryScope(PROJECT), QueryEntityType.TASK,
                Optional.of(QueryPredicate.unary("completed", QueryOperator.EQ, "sometimes")),
                List.of(), QueryProjection.defaults(), QueryPage.first(10));

        assertEquals("QUERY_VALUE_INVALID", validator.validate(query).getFirst().code());
    }

    @Test
    void rejectsPortfolioEntityWithoutPortfolioScope() {
        QueryDefinition query = QueryDefinition.all(
                new ProjectQueryScope(PROJECT), QueryEntityType.PORTFOLIO_REFERENCE, QueryPage.first(10));

        assertEquals("QUERY_SCOPE_INVALID", validator.validate(query).getFirst().code());

        QueryDefinition valid = QueryDefinition.all(
                new PortfolioQueryScope(PortfolioId.generate()), QueryEntityType.PORTFOLIO_REFERENCE, QueryPage.first(10));
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    void rejectsAstPredicateAndEncodedSizeBudgets() {
        List<com.morpheus.application.query.dsl.QueryFilter> predicates = new ArrayList<>();
        for (int index = 0; index < QueryBudgets.MAX_PREDICATES + 1; index++) {
            predicates.add(QueryPredicate.unary("title", QueryOperator.CONTAINS, "term-" + index));
        }
        QueryDefinition query = new QueryDefinition(
                new ProjectQueryScope(PROJECT), QueryEntityType.REQUIREMENT,
                Optional.of(new QueryAnd(predicates)), List.of(), QueryProjection.defaults(), QueryPage.first(10));

        assertTrue(validator.validate(query).stream().anyMatch(item -> item.message().contains("predicates exceed")));
        assertTrue(validator.validate(query, QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES + 1).stream()
                .anyMatch(item -> item.message().contains("encoded query exceeds")));
    }

    @Test
    void rejectsExcessiveBooleanDepth() {
        com.morpheus.application.query.dsl.QueryFilter filter =
                QueryPredicate.unary("title", QueryOperator.CONTAINS, "x");
        for (int index = 0; index < QueryBudgets.MAX_BOOLEAN_DEPTH; index++) {
            filter = new QueryNot(filter);
        }
        QueryDefinition query = new QueryDefinition(
                new ProjectQueryScope(PROJECT), QueryEntityType.REQUIREMENT,
                Optional.of(filter), List.of(), QueryProjection.defaults(), QueryPage.first(10));

        QueryValidationException error = assertThrows(QueryValidationException.class, () -> validator.requireValid(query));
        assertTrue(error.diagnostics().stream().anyMatch(item -> item.message().contains("boolean depth exceeds")));
    }
}
