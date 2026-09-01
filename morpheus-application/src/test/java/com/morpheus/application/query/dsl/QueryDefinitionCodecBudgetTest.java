package com.morpheus.application.query.dsl;

import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryDefinitionCodecBudgetTest {

    private final QueryDefinitionCodec codec = new QueryDefinitionCodec();

    @Test
    void validDefinitionStillRoundTripsDeterministically() {
        QueryDefinition query = queryWithFilter(QueryPredicate.exists("title"));

        String encoded = codec.encode(query);

        assertEquals(query, codec.decode(encoded));
        assertEquals(encoded, codec.encode(codec.decode(encoded)));
    }

    @Test
    void rejectsEncodedPayloadAboveSixteenKiBBeforeBase64Decode() {
        String oversized = "A".repeat(QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES + 1);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(oversized));

        assertTrue(failure.getMessage().contains("exceeds " + QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES));
    }

    @Test
    void rejectsEncoderOutputAboveSixteenKiB() {
        QueryDefinition query = queryWithFilter(
                QueryPredicate.unary("title", QueryOperator.CONTAINS, "x".repeat(20_000)));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> codec.encode(query));

        assertTrue(failure.getMessage().contains("exceeds " + QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES));
    }

    @Test
    void rejectsBooleanDepthBeforeFollowingDeepRecursivePayload() throws IOException {
        String encoded = encodedFilter(out -> {
            for (int depth = 0; depth < 10_000; depth++) {
                out.writeByte(4);
            }
            writeExistsPredicate(out);
        });

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(encoded));

        assertTrue(failure.getMessage().contains("boolean depth exceeds " + QueryBudgets.MAX_BOOLEAN_DEPTH));
    }

    @Test
    void rejectsOneHundredTwentyNinthGlobalAstNode() throws IOException {
        String encoded = encodedFilter(out -> {
            out.writeByte(2);
            out.writeInt(64);
            for (int index = 0; index < 64; index++) {
                out.writeByte(4);
                writeExistsPredicate(out);
            }
        });

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(encoded));

        assertTrue(failure.getMessage().contains("AST nodes exceed " + QueryBudgets.MAX_AST_NODES));
    }

    @Test
    void rejectsSixtyFifthGlobalPredicate() throws IOException {
        String encoded = encodedFilter(out -> {
            out.writeByte(2);
            out.writeInt(QueryBudgets.MAX_PREDICATES + 1);
            for (int index = 0; index <= QueryBudgets.MAX_PREDICATES; index++) {
                writeExistsPredicate(out);
            }
        });

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(encoded));

        assertTrue(failure.getMessage().contains("predicates exceed " + QueryBudgets.MAX_PREDICATES));
    }

    @Test
    void validatorTraversesAndAndOrWithinBudgetWithoutExhaustion() {
        QueryFilter filter = new QueryAnd(List.of(
                QueryPredicate.exists("title"),
                new QueryOr(List.of(
                        QueryPredicate.exists("statement"),
                        QueryPredicate.exists("key")))));

        List<QueryDiagnostic> diagnostics = new QueryValidator().validate(queryWithFilter(filter));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void validatorStopsTraversingOnceDepthBudgetIsExceeded() {
        QueryFilter filter = QueryPredicate.exists("title");
        for (int depth = 0; depth < 10_000; depth++) {
            filter = new QueryNot(filter);
        }

        List<QueryDiagnostic> diagnostics = new QueryValidator().validate(queryWithFilter(filter));

        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                diagnostic.message().contains("boolean depth exceeds " + QueryBudgets.MAX_BOOLEAN_DEPTH)));
    }

    @Test
    void validatorStopsAtOneHundredTwentyNinthGlobalAstNode() {
        List<QueryFilter> children = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            children.add(new QueryNot(QueryPredicate.exists("title")));
        }

        List<QueryDiagnostic> diagnostics = new QueryValidator().validate(queryWithFilter(new QueryAnd(children)));

        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                diagnostic.message().contains("AST nodes exceed " + QueryBudgets.MAX_AST_NODES)));
    }

    @Test
    void validatorStopsAtSixtyFifthGlobalPredicate() {
        List<QueryFilter> children = new ArrayList<>();
        for (int index = 0; index <= QueryBudgets.MAX_PREDICATES; index++) {
            children.add(QueryPredicate.exists("title"));
        }

        List<QueryDiagnostic> diagnostics = new QueryValidator().validate(queryWithFilter(new QueryAnd(children)));

        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                diagnostic.message().contains("predicates exceed " + QueryBudgets.MAX_PREDICATES)));
    }

    @Test
    void portfolioScopedQueryRoundTripsThroughTheNonProjectScopeBranch() {
        QueryDefinition query = new QueryDefinition(
                new PortfolioQueryScope(PortfolioId.generate()),
                QueryEntityType.REQUIREMENT,
                Optional.of(QueryPredicate.exists("title")),
                List.of(),
                QueryProjection.defaults(),
                QueryPage.first(10));

        String encoded = codec.encode(query);
        QueryDefinition decoded = codec.decode(encoded);

        assertEquals(query, decoded);
        assertTrue(decoded.scope() instanceof PortfolioQueryScope);
    }

    @Test
    void conjunctionFilterRoundTripsThroughTheQueryAndBranch() {
        QueryFilter filter = new QueryAnd(List.of(
                QueryPredicate.exists("title"),
                QueryPredicate.exists("key")));

        QueryDefinition query = queryWithFilter(filter);
        String encoded = codec.encode(query);
        QueryDefinition decoded = codec.decode(encoded);

        assertEquals(query, decoded);
        assertTrue(decoded.filter().orElseThrow() instanceof QueryAnd);
    }

    private QueryDefinition queryWithFilter(QueryFilter filter) {
        return new QueryDefinition(
                new ProjectQueryScope(ProjectSpecificationId.generate()),
                QueryEntityType.REQUIREMENT,
                Optional.of(filter),
                List.of(),
                QueryProjection.defaults(),
                QueryPage.first(10));
    }

    private String encodedFilter(FilterWriter writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(1);
            out.writeByte(1);
            out.writeUTF(ProjectSpecificationId.generate().toString());
            out.writeUTF(QueryEntityType.REQUIREMENT.name());
            out.writeBoolean(true);
            writer.write(out);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(10);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    private static void writeExistsPredicate(DataOutputStream out) throws IOException {
        out.writeByte(1);
        out.writeUTF("title");
        out.writeUTF(QueryOperator.EXISTS.name());
        out.writeInt(0);
    }

    @FunctionalInterface
    private interface FilterWriter {
        void write(DataOutputStream out) throws IOException;
    }
}
