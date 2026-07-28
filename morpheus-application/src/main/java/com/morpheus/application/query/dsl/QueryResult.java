package com.morpheus.application.query.dsl;

import java.util.List;
import java.util.Objects;

/** Deterministic paged result produced by the M24 query engine. */
public record QueryResult(
        QueryDefinition query,
        List<String> columns,
        List<QueryRow> items,
        int totalMatches,
        boolean hasMore) {

    public QueryResult {
        Objects.requireNonNull(query, "query");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (totalMatches < 0) {
            throw new IllegalArgumentException("totalMatches must be non-negative");
        }
        if (items.size() > query.page().limit()) {
            throw new IllegalArgumentException("result items exceed query page limit");
        }
    }
}
