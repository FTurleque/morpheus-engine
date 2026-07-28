package com.morpheus.application.query.dsl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Central parser for the small provider-neutral M24 text DSL used by public adapters.
 * Grammar is intentionally bounded and contains no SQL/provider/store concepts.
 */
public final class QueryDslParser {
    private final QueryValidator validator = new QueryValidator();

    public QueryDefinition parse(
            QueryScope scope,
            String entity,
            String filterExpression,
            String sortExpression,
            String projectionExpression,
            int offset,
            int limit) {
        Objects.requireNonNull(scope, "scope");
        QueryEntityType entityType = entityType(entity);
        Optional<QueryFilter> filter = optional(filterExpression).map(this::filter);
        List<QuerySort> sort = sort(sortExpression);
        QueryProjection projection = projection(projectionExpression);
        QueryDefinition definition = new QueryDefinition(
                scope, entityType, filter, sort, projection, new QueryPage(offset, limit));
        int encodedBytes = String.join("\n",
                        normalize(entity), normalize(filterExpression), normalize(sortExpression), normalize(projectionExpression))
                .getBytes(StandardCharsets.UTF_8).length;
        validator.requireValid(definition, encodedBytes);
        return definition;
    }

    public QueryFilter filter(String expression) {
        String normalized = Objects.requireNonNull(expression, "expression").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("filter expression must not be blank");
        }
        if (normalized.getBytes(StandardCharsets.UTF_8).length > QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES) {
            throw new QueryValidationException(
                    List.of(new QueryDiagnostic(
                            "QUERY_BUDGET_EXCEEDED", "$.filter",
                            "encoded query exceeds " + QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES + " bytes")),
                    "QUERY_BUDGET_EXCEEDED at $.filter: encoded query exceeds "
                            + QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES + " bytes");
        }
        Parser parser = new Parser(normalized);
        QueryFilter result = parser.parseExpression(1);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("unexpected trailing input");
        }
        return result;
    }

    private QueryEntityType entityType(String raw) {
        String normalized = require(raw, "entity")
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return QueryEntityType.valueOf(normalized);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("unknown query entity: " + raw);
        }
    }

    private List<QuerySort> sort(String expression) {
        Optional<String> normalized = optional(expression);
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<QuerySort> result = new ArrayList<>();
        for (String token : normalized.orElseThrow().split(",")) {
            String item = token.trim();
            int separator = item.lastIndexOf(':');
            if (separator <= 0 || separator == item.length() - 1) {
                throw new IllegalArgumentException("sort must use field:asc or field:desc syntax");
            }
            String field = item.substring(0, separator).trim();
            String direction = item.substring(separator + 1).trim().toUpperCase(Locale.ROOT);
            try {
                result.add(new QuerySort(field, QuerySortDirection.valueOf(direction)));
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("invalid sort direction: " + direction);
            }
        }
        return List.copyOf(result);
    }

    private QueryProjection projection(String expression) {
        Optional<String> normalized = optional(expression);
        if (normalized.isEmpty()) {
            return QueryProjection.defaults();
        }
        return new QueryProjection(java.util.Arrays.stream(normalized.orElseThrow().split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList());
    }

    private static Optional<String> optional(String value) {
        return Optional.ofNullable(value).map(String::trim).filter(item -> !item.isEmpty());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String require(String value, String name) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static final class Parser {
        private final String input;
        private int position;
        private int nodes;
        private int predicates;

        private Parser(String input) {
            this.input = input;
        }

        private QueryFilter parseExpression(int depth) {
            budget(depth);
            skipWhitespace();
            int start = position;
            String first = identifier();
            skipWhitespace();
            if ((first.equalsIgnoreCase("and") || first.equalsIgnoreCase("or") || first.equalsIgnoreCase("not"))
                    && peek('(')) {
                position++;
                if (first.equalsIgnoreCase("not")) {
                    QueryFilter child = parseExpression(depth + 1);
                    skipWhitespace();
                    expect(')');
                    return new QueryNot(child);
                }
                List<QueryFilter> children = new ArrayList<>();
                while (true) {
                    children.add(parseExpression(depth + 1));
                    skipWhitespace();
                    if (peek(',')) {
                        position++;
                        continue;
                    }
                    expect(')');
                    break;
                }
                return first.equalsIgnoreCase("and") ? new QueryAnd(children) : new QueryOr(children);
            }
            position = start;
            return predicate();
        }

        private QueryPredicate predicate() {
            predicates++;
            if (predicates > QueryBudgets.MAX_PREDICATES) {
                throw error("predicate budget exceeds " + QueryBudgets.MAX_PREDICATES);
            }
            String field = identifier();
            requireWhitespace("predicate operator");
            String rawOperator = identifier().replace('-', '_').toUpperCase(Locale.ROOT);
            QueryOperator operator;
            try {
                operator = QueryOperator.valueOf(rawOperator);
            } catch (IllegalArgumentException failure) {
                throw error("unknown query operator: " + rawOperator);
            }
            if (operator == QueryOperator.EXISTS) {
                return QueryPredicate.exists(field);
            }
            requireWhitespace("predicate value");
            if (operator == QueryOperator.IN) {
                return new QueryPredicate(field, operator, list());
            }
            return QueryPredicate.unary(field, operator, value());
        }

        private List<String> list() {
            skipWhitespace();
            expect('[');
            List<String> values = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                throw error("IN list must not be empty");
            }
            while (true) {
                values.add(value());
                skipWhitespace();
                if (peek(',')) {
                    position++;
                    continue;
                }
                expect(']');
                return List.copyOf(values);
            }
        }

        private String value() {
            skipWhitespace();
            if (peek('"')) {
                return quoted();
            }
            int start = position;
            while (!atEnd()) {
                char current = input.charAt(position);
                if (Character.isWhitespace(current) || current == ',' || current == ')' || current == ']') {
                    break;
                }
                position++;
            }
            if (position == start) {
                throw error("expected value");
            }
            return input.substring(start, position);
        }

        private String quoted() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (!atEnd()) {
                char current = input.charAt(position++);
                if (current == '"') {
                    return value.toString();
                }
                if (current != '\\') {
                    value.append(current);
                    continue;
                }
                if (atEnd()) {
                    throw error("unterminated escape sequence");
                }
                char escaped = input.charAt(position++);
                value.append(switch (escaped) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> throw error("unsupported escape sequence: \\" + escaped);
                });
            }
            throw error("unterminated quoted value");
        }

        private String identifier() {
            skipWhitespace();
            int start = position;
            while (!atEnd()) {
                char current = input.charAt(position);
                if (!(Character.isLetterOrDigit(current) || current == '_' || current == '-' || current == '.')) {
                    break;
                }
                position++;
            }
            if (position == start) {
                throw error("expected identifier");
            }
            return input.substring(start, position);
        }

        private void budget(int depth) {
            nodes++;
            if (nodes > QueryBudgets.MAX_AST_NODES) {
                throw error("AST node budget exceeds " + QueryBudgets.MAX_AST_NODES);
            }
            if (depth > QueryBudgets.MAX_BOOLEAN_DEPTH) {
                throw error("boolean depth budget exceeds " + QueryBudgets.MAX_BOOLEAN_DEPTH);
            }
        }

        private void requireWhitespace(String next) {
            if (atEnd() || !Character.isWhitespace(input.charAt(position))) {
                throw error("expected whitespace before " + next);
            }
            skipWhitespace();
        }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (atEnd() || input.charAt(position) != expected) {
                throw error("expected '" + expected + "'");
            }
            position++;
        }

        private boolean peek(char expected) {
            return !atEnd() && input.charAt(position) == expected;
        }

        private boolean atEnd() {
            return position >= input.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("query DSL parse error at position " + position + ": " + message);
        }
    }
}
