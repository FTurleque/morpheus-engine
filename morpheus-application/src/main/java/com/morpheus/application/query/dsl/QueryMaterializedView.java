package com.morpheus.application.query.dsl;

import java.util.List;
import java.util.Objects;

/** Complete projected result from one bounded query materialization. */
public record QueryMaterializedView(
        QueryDefinition query,
        List<String> columns,
        List<QueryRow> items) {

    public QueryMaterializedView {
        Objects.requireNonNull(query, "query");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public int totalMatches() {
        return items.size();
    }
}
