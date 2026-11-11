package com.morpheus.application.query.dsl;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The validator reports every defect of a query as a diagnostic with a code and a path, sorted by path. */
class QueryValidatorTest {
    private final QueryValidator validator = new QueryValidator();
    private final QueryScope scope = new ProjectQueryScope(ProjectSpecificationId.generate());

    @Test
    void aSizeOutsideItsBoundsIsADiagnostic() {
        QueryDefinition query = QueryDefinition.all(scope, QueryEntityType.TASK, QueryPage.first(1));

        assertEquals(List.of(new QueryDiagnostic("QUERY_SIZE_INVALID", "$", "encoded query size must be non-negative")),
                validator.validate(query, -1));
        assertEquals(List.of(new QueryDiagnostic("QUERY_BUDGET_EXCEEDED", "$",
                        "encoded query exceeds " + QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES + " bytes")),
                validator.validate(query, QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES + 1));
    }

    @Test
    void anUnknownProjectionFieldIsNamedWithItsIndex() {
        QueryDefinition query = new QueryDefinition(scope, QueryEntityType.TASK, Optional.empty(), List.of(),
                new QueryProjection(List.of("title", "colour")), QueryPage.first(1));

        assertEquals(List.of(new QueryDiagnostic("QUERY_FIELD_UNKNOWN", "$.projection[1]", "unknown field: colour")),
                validator.validate(query));
    }

    @Test
    void anOperatorTheFieldTypeDoesNotSupportIsRefused() {
        List<QueryDiagnostic> diagnostics = validator.validate(filtered(
                QueryPredicate.unary("completed", QueryOperator.CONTAINS, "tr")));

        assertEquals(List.of(new QueryDiagnostic("QUERY_OPERATOR_INVALID", "$.filter.operator",
                "CONTAINS is not supported for BOOLEAN field completed")), diagnostics);
    }

    @Test
    void aBooleanLiteralMustBeTrueOrFalseInAnyCase() {
        assertEquals(List.of(), validator.validate(filtered(QueryPredicate.unary("completed", QueryOperator.EQ, "TRUE"))));
        assertEquals(List.of(), validator.validate(filtered(QueryPredicate.unary("completed", QueryOperator.EQ, "false"))));
        assertEquals(List.of(new QueryDiagnostic("QUERY_VALUE_INVALID", "$.filter.values[1]",
                        "invalid BOOLEAN literal for field completed")),
                validator.validate(filtered(new QueryPredicate("completed", QueryOperator.IN, List.of("true", "yes")))));
    }

    @Test
    void anExistsPredicateCarriesNoValueToValidate() {
        assertEquals(List.of(), validator.validate(filtered(QueryPredicate.exists("completed"))));
    }

    private QueryDefinition filtered(QueryFilter filter) {
        return new QueryDefinition(scope, QueryEntityType.TASK, Optional.of(filter), List.of(),
                QueryProjection.defaults(), QueryPage.first(1));
    }
}
