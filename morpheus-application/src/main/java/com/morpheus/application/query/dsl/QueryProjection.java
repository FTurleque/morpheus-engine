package com.morpheus.application.query.dsl;

import java.util.List;
import java.util.Objects;

/** Optional ordered list of fields returned by a query. Empty means the entity default projection. */
public record QueryProjection(List<String> fields) {
    public QueryProjection {
        Objects.requireNonNull(fields, "fields");
        fields = fields.stream()
                .map(field -> Objects.requireNonNull(field, "projection field").trim())
                .peek(field -> {
                    if (field.isEmpty()) {
                        throw new IllegalArgumentException("projection field must not be blank");
                    }
                })
                .distinct()
                .toList();
    }

    public static QueryProjection defaults() {
        return new QueryProjection(List.of());
    }
}
