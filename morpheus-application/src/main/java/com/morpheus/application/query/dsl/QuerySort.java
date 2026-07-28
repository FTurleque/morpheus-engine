package com.morpheus.application.query.dsl;

import java.util.Objects;

/** One requested business-field ordering key. */
public record QuerySort(String field, QuerySortDirection direction) {
    public QuerySort {
        Objects.requireNonNull(field, "field");
        field = field.trim();
        if (field.isEmpty()) {
            throw new IllegalArgumentException("sort field must not be blank");
        }
        Objects.requireNonNull(direction, "direction");
    }
}
