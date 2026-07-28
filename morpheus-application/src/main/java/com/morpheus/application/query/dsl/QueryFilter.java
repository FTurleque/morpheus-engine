package com.morpheus.application.query.dsl;

/** Boolean filter node for the bounded M24 query AST. */
public sealed interface QueryFilter permits QueryPredicate, QueryAnd, QueryOr, QueryNot {
}
