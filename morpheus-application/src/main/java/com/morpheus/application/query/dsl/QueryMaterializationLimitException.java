package com.morpheus.application.query.dsl;

/** Raised before full projection when a complete materialization would exceed its caller-supplied row budget. */
public final class QueryMaterializationLimitException extends IllegalStateException {
    private final int maximumRows;
    private final int actualRows;

    public QueryMaterializationLimitException(int maximumRows, int actualRows) {
        super("query materialization exceeds row budget " + maximumRows + ": " + actualRows);
        if (maximumRows < 1) {
            throw new IllegalArgumentException("maximumRows must be positive");
        }
        if (actualRows <= maximumRows) {
            throw new IllegalArgumentException("actualRows must exceed maximumRows");
        }
        this.maximumRows = maximumRows;
        this.actualRows = actualRows;
    }

    public int maximumRows() {
        return maximumRows;
    }

    public int actualRows() {
        return actualRows;
    }
}
