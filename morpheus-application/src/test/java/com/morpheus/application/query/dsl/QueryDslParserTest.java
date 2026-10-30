package com.morpheus.application.query.dsl;

import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The text DSL every transport shares: what it accepts, what it builds, and how it names what it refuses. */
class QueryDslParserTest {
    private final QueryDslParser parser = new QueryDslParser();
    private final QueryScope project = new ProjectQueryScope(ProjectSpecificationId.generate());

    @Test
    void aFullQueryParsesItsEntityFilterSortProjectionAndPage() {
        QueryDefinition query = parser.parse(project, "acceptance-criterion",
                "and(title contains login, not(verificationStatus eq PASSED))",
                "title:asc, id:desc", "id, title, , id", 5, 20);

        assertEquals(QueryEntityType.ACCEPTANCE_CRITERION, query.entityType());
        QueryAnd and = assertInstanceOf(QueryAnd.class, query.filter().orElseThrow());
        assertEquals(QueryPredicate.unary("title", QueryOperator.CONTAINS, "login"), and.children().getFirst());
        QueryNot not = assertInstanceOf(QueryNot.class, and.children().get(1));
        assertEquals(QueryPredicate.unary("verificationStatus", QueryOperator.EQ, "PASSED"), not.child());
        assertEquals(List.of(new QuerySort("title", QuerySortDirection.ASC), new QuerySort("id", QuerySortDirection.DESC)),
                query.sort());
        assertEquals(List.of("id", "title"), query.projection().fields());
        assertEquals(new QueryPage(5, 20), query.page());
    }

    @Test
    void blankOptionalPartsMeanNoFilterNoSortAndTheDefaultProjection() {
        QueryDefinition query = parser.parse(project, " requirement ", "  ", null, "", 0, 10);

        assertEquals(Optional.empty(), query.filter());
        assertEquals(List.of(), query.sort());
        assertEquals(QueryProjection.defaults(), query.projection());
    }

    @Test
    void orInExistsAndQuotedValuesWithEscapesAreParsed() {
        QueryFilter filter = parser.filter(
                "or(key in [A-1, \"B 2\"], providerId exists, title eq \"say \\\"hi\\\"\\n\\t\\r\\\\\")");

        QueryOr or = assertInstanceOf(QueryOr.class, filter);
        assertEquals(new QueryPredicate("key", QueryOperator.IN, List.of("A-1", "B 2")), or.children().getFirst());
        assertEquals(QueryPredicate.exists("providerId"), or.children().get(1));
        assertEquals(QueryPredicate.unary("title", QueryOperator.EQ, "say \"hi\"\n\t\r\\".trim()), or.children().get(2));
    }

    @Test
    void anIdentifierNamedLikeAnOperatorIsAFieldWhenNoParenthesisFollows() {
        QueryFilter filter = parser.filter("and eq x");

        assertEquals(QueryPredicate.unary("and", QueryOperator.EQ, "x"), filter);
    }

    @Test
    void anUnknownEntityIsRefusedByItsRawName() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(project, "widget", null, null, null, 0, 10));
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(project, " ", null, null, null, 0, 10));

        assertEquals("unknown query entity: widget", refused.getMessage());
        assertEquals("entity must not be blank", blank.getMessage());
    }

    @Test
    void aSortWithoutADirectionOrWithAnUnknownOneIsRefused() {
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(project, "requirement", null, "title", null, 0, 10));
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(project, "requirement", null, "title:", null, 0, 10));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(project, "requirement", null, "title:up", null, 0, 10));

        assertEquals("sort must use field:asc or field:desc syntax", missing.getMessage());
        assertEquals("sort must use field:asc or field:desc syntax", empty.getMessage());
        assertEquals("invalid sort direction: UP", unknown.getMessage());
    }

    @Test
    void aParsedQueryIsValidatedAgainstTheSchemaOfItsEntity() {
        QueryValidationException refused = assertThrows(QueryValidationException.class,
                () -> parser.parse(project, "requirement", "colour eq red", null, null, 0, 10));

        assertEquals("QUERY_FIELD_UNKNOWN at $.filter.field: unknown field: colour", refused.getMessage());
        assertEquals("QUERY_FIELD_UNKNOWN", refused.diagnostics().getFirst().code());
    }

    @Test
    void aPortfolioEntityNeedsAPortfolioScope() {
        QueryValidationException refused = assertThrows(QueryValidationException.class,
                () -> parser.parse(project, "portfolio-membership", null, null, null, 0, 10));
        QueryDefinition accepted = parser.parse(new PortfolioQueryScope(PortfolioId.generate()),
                "portfolio-membership", null, null, null, 0, 10);

        assertEquals("QUERY_SCOPE_INVALID at $.scope: PORTFOLIO_MEMBERSHIP requires a portfolio scope",
                refused.getMessage());
        assertEquals(QueryEntityType.PORTFOLIO_MEMBERSHIP, accepted.entityType());
    }

    @Test
    void malformedFiltersAreRefusedWithTheirPosition() {
        assertAll(
                () -> assertEquals("filter expression must not be blank", refusal(" ")),
                () -> assertEquals("query DSL parse error at position 8: expected whitespace before predicate value",
                        refusal("title eq")),
                () -> assertEquals("query DSL parse error at position 5: expected whitespace before predicate operator",
                        refusal("title")),
                () -> assertEquals("query DSL parse error at position 10: unknown query operator: LIKE", refusal("title like x")),
                () -> assertEquals("query DSL parse error at position 11: unexpected trailing input", refusal("title eq x y")),
                () -> assertEquals("query DSL parse error at position 0: expected identifier", refusal("(title eq x)")),
                () -> assertEquals("query DSL parse error at position 9: IN list must not be empty", refusal("key in [ ]")),
                () -> assertEquals("query DSL parse error at position 7: expected '['", refusal("key in A")),
                () -> assertEquals("query DSL parse error at position 13: expected ')'", refusal("and(key eq a b")),
                () -> assertEquals("query DSL parse error at position 13: expected ')'", refusal("not(key eq a b)")),
                () -> assertEquals("query DSL parse error at position 9: expected value", refusal("title eq ,")),
                () -> assertEquals("query DSL parse error at position 14: unterminated quoted value", refusal("title eq \"open")),
                () -> assertEquals("query DSL parse error at position 14: unterminated escape sequence", refusal("title eq \"abc\\")),
                () -> assertEquals("query DSL parse error at position 15: unsupported escape sequence: \\x",
                        refusal("title eq \"abc\\x\"")));
    }

    @Test
    void aFilterDeeperThanTheBooleanBudgetIsRefused() {
        String filter = "not(".repeat(QueryBudgets.MAX_BOOLEAN_DEPTH) + "key eq a" + ")".repeat(QueryBudgets.MAX_BOOLEAN_DEPTH);

        String message = refusal(filter);

        assertTrue(message.contains("boolean depth budget exceeds " + QueryBudgets.MAX_BOOLEAN_DEPTH), message);
    }

    @Test
    void aFilterWithMorePredicatesThanTheBudgetIsRefused() {
        String filter = "or(" + String.join(", ", java.util.Collections.nCopies(QueryBudgets.MAX_PREDICATES + 1, "key eq a"))
                + ")";

        String message = refusal(filter);

        assertTrue(message.contains("budget exceeds"), message);
    }

    private String refusal(String filter) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> parser.filter(filter));
        return refused.getMessage();
    }
}
