package com.morpheus.application.query.dsl;

import java.util.Objects;

/** Negation of one filter node. */
public record QueryNot(QueryFilter child) implements QueryFilter {
    public QueryNot {
        Objects.requireNonNull(child, "child");
    }
}
