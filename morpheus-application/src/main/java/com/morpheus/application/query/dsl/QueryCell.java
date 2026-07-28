package com.morpheus.application.query.dsl;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Transport-neutral projected values for one registered query field. */
public record QueryCell(String field, List<String> values) {
    public QueryCell {
        Objects.requireNonNull(field, "field");
        field = field.trim();
        if (field.isEmpty()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        Objects.requireNonNull(values, "values");
        values = values.stream()
                .map(value -> Objects.requireNonNull(value, "cell value"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public static QueryCell scalar(String field, Object value) {
        return new QueryCell(field, value == null ? List.of() : List.of(value.toString()));
    }

    public static QueryCell optional(String field, Optional<?> value) {
        Objects.requireNonNull(value, "value");
        return new QueryCell(field, value.map(Object::toString).stream().toList());
    }

    public Optional<String> first() {
        return values.stream().findFirst();
    }

    public String render() {
        return String.join(", ", values);
    }

    public String sortKey() {
        return String.join("\u001f", values).toLowerCase(Locale.ROOT);
    }
}
