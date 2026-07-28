package com.morpheus.application.query.dsl;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable root of one provider-neutral M24 query. */
public record QueryDefinition(
        QueryScope scope,
        QueryEntityType entityType,
        Optional<QueryFilter> filter,
        List<QuerySort> sort,
        QueryProjection projection,
        QueryPage page) {

    public QueryDefinition {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(entityType, "entityType");
        filter = Objects.requireNonNull(filter, "filter");
        sort = Objects.requireNonNull(sort, "sort").stream()
                .map(item -> Objects.requireNonNull(item, "sort item"))
                .toList();
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(page, "page");
    }

    public static QueryDefinition all(
            QueryScope scope,
            QueryEntityType entityType,
            QueryPage page) {
        return new QueryDefinition(scope, entityType, Optional.empty(), List.of(), QueryProjection.defaults(), page);
    }
}
