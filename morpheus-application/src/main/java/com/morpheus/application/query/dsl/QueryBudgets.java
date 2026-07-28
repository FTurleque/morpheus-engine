package com.morpheus.application.query.dsl;

/** Centralized hard limits for M24 query, saved-view and export contracts. */
public final class QueryBudgets {
    public static final int MAX_ENCODED_EXPRESSION_BYTES = 16 * 1024;
    public static final int MAX_AST_NODES = 128;
    public static final int MAX_BOOLEAN_DEPTH = 8;
    public static final int MAX_PREDICATES = 64;
    public static final int MAX_SORT_FIELDS = 8;
    public static final int MAX_PROJECTION_FIELDS = 32;
    public static final int MAX_PAGE_SIZE = 500;
    public static final int MAX_EXPORT_ROWS = 10_000;
    public static final int MAX_EXPORT_BYTES = 10 * 1024 * 1024;
    public static final int MAX_SAVED_VIEWS_PER_SCOPE = 250;
    public static final int MAX_SAVED_VIEW_NAME = 160;

    private QueryBudgets() {
    }
}
