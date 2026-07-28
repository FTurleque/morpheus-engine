package com.morpheus.application.query.dsl;

/** Bounded offset pagination for M24 queries. */
public record QueryPage(int offset, int limit) {
    public QueryPage {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be greater than or equal to zero");
        }
        if (limit <= 0 || limit > QueryBudgets.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + QueryBudgets.MAX_PAGE_SIZE);
        }
    }

    public static QueryPage first(int limit) {
        return new QueryPage(0, limit);
    }
}
