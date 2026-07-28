package com.morpheus.application.query.dsl;

/** Closed set of provider-neutral predicate operators supported by M24. */
public enum QueryOperator {
    EQ,
    NEQ,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    /** Parser alias for startsWith; QueryPredicate canonicalizes it to STARTS_WITH. */
    STARTSWITH,
    /** Parser alias for endsWith; QueryPredicate canonicalizes it to ENDS_WITH. */
    ENDSWITH,
    IN,
    EXISTS;

    public QueryOperator canonical() {
        return switch (this) {
            case STARTSWITH -> STARTS_WITH;
            case ENDSWITH -> ENDS_WITH;
            default -> this;
        };
    }
}
