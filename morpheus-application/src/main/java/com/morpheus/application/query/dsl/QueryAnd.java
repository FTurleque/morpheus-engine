package com.morpheus.application.query.dsl;

import java.util.List;
import java.util.Objects;

/** Conjunction of two or more filters. */
public record QueryAnd(List<QueryFilter> children) implements QueryFilter {
    public QueryAnd {
        Objects.requireNonNull(children, "children");
        children = children.stream().map(child -> Objects.requireNonNull(child, "child")).toList();
        if (children.size() < 2) {
            throw new IllegalArgumentException("AND requires at least two children");
        }
    }
}
