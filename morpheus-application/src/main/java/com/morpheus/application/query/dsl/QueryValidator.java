package com.morpheus.application.query.dsl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Single application-level validation authority for M24 query semantics and budgets. */
public final class QueryValidator {

    public List<QueryDiagnostic> validate(QueryDefinition query) {
        return validate(query, 0);
    }

    public List<QueryDiagnostic> validate(QueryDefinition query, int encodedExpressionBytes) {
        List<QueryDiagnostic> diagnostics = new ArrayList<>();
        if (encodedExpressionBytes < 0) {
            diagnostics.add(new QueryDiagnostic("QUERY_SIZE_INVALID", "$", "encoded query size must be non-negative"));
        } else if (encodedExpressionBytes > QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES) {
            diagnostics.add(new QueryDiagnostic(
                    "QUERY_BUDGET_EXCEEDED", "$",
                    "encoded query exceeds " + QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES + " bytes"));
        }

        if ((query.entityType() == QueryEntityType.PORTFOLIO_MEMBERSHIP
                || query.entityType() == QueryEntityType.PORTFOLIO_REFERENCE)
                && !(query.scope() instanceof PortfolioQueryScope)) {
            diagnostics.add(new QueryDiagnostic(
                    "QUERY_SCOPE_INVALID", "$.scope",
                    query.entityType() + " requires a portfolio scope"));
        }

        Map<String, QueryFieldDefinition> fields = QuerySchemaRegistry.fields(query.entityType());

        if (query.sort().size() > QueryBudgets.MAX_SORT_FIELDS) {
            diagnostics.add(new QueryDiagnostic(
                    "QUERY_BUDGET_EXCEEDED", "$.sort",
                    "sort fields exceed " + QueryBudgets.MAX_SORT_FIELDS));
        }
        Set<String> seenSort = new HashSet<>();
        for (int index = 0; index < query.sort().size(); index++) {
            QuerySort sort = query.sort().get(index);
            String path = "$.sort[" + index + "].field";
            if (!fields.containsKey(sort.field())) {
                diagnostics.add(new QueryDiagnostic("QUERY_FIELD_UNKNOWN", path, "unknown field: " + sort.field()));
            }
            if (!seenSort.add(sort.field())) {
                diagnostics.add(new QueryDiagnostic("QUERY_SORT_DUPLICATE", path, "duplicate sort field: " + sort.field()));
            }
        }

        if (query.projection().fields().size() > QueryBudgets.MAX_PROJECTION_FIELDS) {
            diagnostics.add(new QueryDiagnostic(
                    "QUERY_BUDGET_EXCEEDED", "$.projection",
                    "projection fields exceed " + QueryBudgets.MAX_PROJECTION_FIELDS));
        }
        for (int index = 0; index < query.projection().fields().size(); index++) {
            String field = query.projection().fields().get(index);
            if (!fields.containsKey(field)) {
                diagnostics.add(new QueryDiagnostic(
                        "QUERY_FIELD_UNKNOWN", "$.projection[" + index + "]", "unknown field: " + field));
            }
        }

        query.filter().ifPresent(filter -> inspect(filter, fields, "$.filter", 1, new Counters(), diagnostics));
        return diagnostics.stream().sorted().toList();
    }

    public void requireValid(QueryDefinition query) {
        requireValid(query, 0);
    }

    public void requireValid(QueryDefinition query, int encodedExpressionBytes) {
        List<QueryDiagnostic> diagnostics = validate(query, encodedExpressionBytes);
        if (!diagnostics.isEmpty()) {
            QueryDiagnostic first = diagnostics.getFirst();
            throw new QueryValidationException(diagnostics, first.code() + " at " + first.path() + ": " + first.message());
        }
    }

    private void inspect(
            QueryFilter filter,
            Map<String, QueryFieldDefinition> fields,
            String path,
            int depth,
            Counters counters,
            List<QueryDiagnostic> diagnostics) {
        counters.nodes++;
        if (counters.nodes > QueryBudgets.MAX_AST_NODES) {
            diagnostics.add(new QueryDiagnostic(
                    "QUERY_BUDGET_EXCEEDED", path, "AST nodes exceed " + QueryBudgets.MAX_AST_NODES));
            counters.exhausted = true;
            return;
        }
        if (depth > QueryBudgets.MAX_BOOLEAN_DEPTH) {
            diagnostics.add(new QueryDiagnostic(
                    "QUERY_BUDGET_EXCEEDED", path, "boolean depth exceeds " + QueryBudgets.MAX_BOOLEAN_DEPTH));
            counters.exhausted = true;
            return;
        }

        if (filter instanceof QueryPredicate predicate) {
            counters.predicates++;
            if (counters.predicates > QueryBudgets.MAX_PREDICATES) {
                diagnostics.add(new QueryDiagnostic(
                        "QUERY_BUDGET_EXCEEDED", path, "predicates exceed " + QueryBudgets.MAX_PREDICATES));
                counters.exhausted = true;
                return;
            }
            validatePredicate(predicate, fields, path, diagnostics);
            return;
        }
        if (filter instanceof QueryAnd and) {
            inspectChildren(and.children(), fields, path + ".and", depth, counters, diagnostics);
            return;
        }
        if (filter instanceof QueryOr or) {
            inspectChildren(or.children(), fields, path + ".or", depth, counters, diagnostics);
            return;
        }
        if (filter instanceof QueryNot not) {
            inspect(not.child(), fields, path + ".not", depth + 1, counters, diagnostics);
            return;
        }
        diagnostics.add(new QueryDiagnostic("QUERY_FILTER_UNKNOWN", path, "unsupported query filter node"));
    }

    private void inspectChildren(
            List<QueryFilter> children,
            Map<String, QueryFieldDefinition> fields,
            String path,
            int parentDepth,
            Counters counters,
            List<QueryDiagnostic> diagnostics) {
        for (int index = 0; index < children.size(); index++) {
            inspect(children.get(index), fields, path + "[" + index + "]", parentDepth + 1, counters, diagnostics);
            if (counters.exhausted) {
                return;
            }
        }
    }

    private void validatePredicate(
            QueryPredicate predicate,
            Map<String, QueryFieldDefinition> fields,
            String path,
            List<QueryDiagnostic> diagnostics) {
        QueryFieldDefinition field = fields.get(predicate.field());
        if (field == null) {
            diagnostics.add(new QueryDiagnostic(
                    "QUERY_FIELD_UNKNOWN", path + ".field", "unknown field: " + predicate.field()));
            return;
        }
        if (!field.operators().contains(predicate.operator())) {
            diagnostics.add(new QueryDiagnostic(
                    "QUERY_OPERATOR_INVALID", path + ".operator",
                    predicate.operator() + " is not supported for " + field.type() + " field " + field.name()));
            return;
        }
        if (predicate.operator() == QueryOperator.EXISTS) {
            return;
        }
        for (int index = 0; index < predicate.values().size(); index++) {
            String value = predicate.values().get(index);
            if (!validLiteral(field.type(), value)) {
                diagnostics.add(new QueryDiagnostic(
                        "QUERY_VALUE_INVALID", path + ".values[" + index + "]",
                        "invalid " + field.type() + " literal for field " + field.name()));
            }
        }
    }

    private boolean validLiteral(QueryFieldType type, String value) {
        return switch (type) {
            case TEXT, IDENTITY, ENUM -> !value.isEmpty();
            case BOOLEAN -> value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
            case NUMBER -> numeric(value);
        };
    }

    private boolean numeric(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static final class Counters {
        private int nodes;
        private int predicates;
        private boolean exhausted;
    }
}
