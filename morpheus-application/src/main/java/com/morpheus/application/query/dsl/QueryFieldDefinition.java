package com.morpheus.application.query.dsl;

import java.util.Objects;
import java.util.Set;

/** Closed schema entry for one provider-neutral business field. */
public record QueryFieldDefinition(String name, QueryFieldType type, Set<QueryOperator> operators, boolean identity) {
    public QueryFieldDefinition {
        Objects.requireNonNull(name, "name");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("field name must not be blank");
        }
        Objects.requireNonNull(type, "type");
        operators = Set.copyOf(Objects.requireNonNull(operators, "operators"));
        if (operators.isEmpty()) {
            throw new IllegalArgumentException("field operators must not be empty");
        }
    }
}
