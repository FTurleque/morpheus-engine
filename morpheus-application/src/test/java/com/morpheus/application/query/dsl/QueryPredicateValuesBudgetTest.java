package com.morpheus.application.query.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.domain.project.ProjectSpecificationId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What the system accepts to write, it can read back.
 *
 * <p>The codec bounded the values of one IN predicate at decode with the constant that bounds the number of
 * predicates of a whole query (64), while no input boundary bounded them at all. A saved view with 65 keys was
 * created, persisted, and then could not be read. Parse, validation, encode and decode now share
 * {@link QueryBudgets#MAX_PREDICATE_VALUES}.</p>
 */
class QueryPredicateValuesBudgetTest {
    private static final int LIMIT = QueryBudgets.MAX_PREDICATE_VALUES;

    private final QueryDefinitionCodec codec = new QueryDefinitionCodec();
    private final QueryDslParser parser = new QueryDslParser();

    @Test
    void theTwoBudgetsAreDistinctQuantities() {
        assertTrue(LIMIT > QueryBudgets.MAX_PREDICATES,
                "values of one IN and predicates of a query are different quantities");
    }

    @Test
    void anInListOverTheBoundIsRefusedAtParseNamingTheBoundAndTheField() {
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> parser.filter("title in [" + values(LIMIT + 1) + "]"));

        assertTrue(failure.getMessage().contains(Integer.toString(LIMIT)), failure.getMessage());
        assertTrue(failure.getMessage().contains("title"), failure.getMessage());
    }

    @Test
    void anInListExactlyAtTheBoundIsAcceptedAndRoundTripsThroughTheCodec() {
        QueryFilter filter = parser.filter("title in [" + values(LIMIT) + "]");
        QueryDefinition query = definition(filter);

        QueryDefinition decoded = codec.decode(codec.encode(query));

        assertEquals(query, decoded);
        assertEquals(LIMIT, ((QueryPredicate) decoded.filter().orElseThrow()).values().size());
    }

    @Test
    void aProgrammaticDefinitionOverTheBoundIsRefusedByTheValidatorAndTheEncoder() {
        List<String> tooMany = new ArrayList<>();
        for (int index = 0; index <= LIMIT; index++) {
            tooMany.add("a" + index);
        }
        QueryDefinition query = definition(new QueryPredicate("title", QueryOperator.IN, tooMany));

        List<QueryDiagnostic> diagnostics = new QueryValidator().validate(query);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> codec.encode(query));

        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                diagnostic.message().contains("predicate values exceed " + LIMIT)), diagnostics.toString());
        assertTrue(refused.getMessage().contains(Integer.toString(LIMIT)), refused.getMessage());
    }

    @Test
    void theDecoderRefusesWhatTheEncoderWouldNotWrite() {
        String written = codec.encode(definition(parser.filter("title in [" + values(LIMIT) + "]")));

        assertEquals(LIMIT, ((QueryPredicate) codec.decode(written).filter().orElseThrow()).values().size(),
                "the write bound and the read bound must be the same constant");
    }

    private static String values(int count) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add("a" + index);
        }
        return String.join(",", values);
    }

    private static QueryDefinition definition(QueryFilter filter) {
        return new QueryDefinition(
                new ProjectQueryScope(ProjectSpecificationId.generate()),
                QueryEntityType.REQUIREMENT,
                Optional.of(filter),
                List.of(),
                QueryProjection.defaults(),
                QueryPage.first(10));
    }
}
