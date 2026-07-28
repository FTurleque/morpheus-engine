package com.morpheus.application.query.dsl;

import java.util.List;
import java.util.Objects;

/** Disjunction of two or more filters. */
public record QueryOr(List<QueryFilter> children) implements QueryFilter {
    public QueryOr {
        Objects.requireNonNull(children, "children");
        children = children.stream().map(child -> Objects.requireNonNull(child, "child")).toList();
        if (children.size() < 2) {
            throw new IllegalArgumentException("OR requires at least two children");
        }
    }
}
