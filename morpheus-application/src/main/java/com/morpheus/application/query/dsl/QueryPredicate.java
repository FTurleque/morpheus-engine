package com.morpheus.application.query.dsl;

import java.util.List;
import java.util.Objects;

/** Leaf predicate over one registered business field. */
public record QueryPredicate(String field, QueryOperator operator, List<String> values) implements QueryFilter {
    public QueryPredicate {
        Objects.requireNonNull(field, "field");
        field = field.trim();
        if (field.isEmpty()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(values, "values");
        values = values.stream()
                .map(value -> Objects.requireNonNull(value, "predicate value"))
                .map(String::trim)
                .toList();
        if (operator == QueryOperator.EXISTS && !values.isEmpty()) {
            throw new IllegalArgumentException("EXISTS does not accept values");
        }
        if (operator != QueryOperator.EXISTS && values.isEmpty()) {
            throw new IllegalArgumentException(operator + " requires at least one value");
        }
        if (operator != QueryOperator.IN && operator != QueryOperator.EXISTS && values.size() != 1) {
            throw new IllegalArgumentException(operator + " requires exactly one value");
        }
    }

    public static QueryPredicate unary(String field, QueryOperator operator, String value) {
        return new QueryPredicate(field, operator, List.of(value));
    }

    public static QueryPredicate exists(String field) {
        return new QueryPredicate(field, QueryOperator.EXISTS, List.of());
    }
}
