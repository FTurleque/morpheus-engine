package com.morpheus.application.query.dsl;

/** Closed set of provider-neutral predicate operators supported by M24. */
public enum QueryOperator {
    EQ,
    NEQ,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    IN,
    EXISTS
}
