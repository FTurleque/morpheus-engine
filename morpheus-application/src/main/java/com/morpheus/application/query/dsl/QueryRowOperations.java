package com.morpheus.application.query.dsl;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Pure filtering, sorting and projection semantics for materialized query rows. */
final class QueryRowOperations {

    boolean matches(QueryRow row, QueryFilter filter) {
        if (filter instanceof QueryPredicate predicate) {
            return matchesPredicate(row, predicate);
        }
        if (filter instanceof QueryAnd and) {
            return and.children().stream().allMatch(child -> matches(row, child));
        }
        if (filter instanceof QueryOr or) {
            return or.children().stream().anyMatch(child -> matches(row, child));
        }
        return !matches(row, ((QueryNot) filter).child());
    }

    Comparator<QueryRow> comparator(QueryDefinition query) {
        Comparator<QueryRow> comparator = (left, right) -> 0;
        Map<String, QueryFieldDefinition> schema = QuerySchemaRegistry.fields(query.entityType());
        for (QuerySort sort : query.sort()) {
            QueryFieldDefinition definition = Objects.requireNonNull(
                    schema.get(sort.field()),
                    "validated sort field missing from query schema: " + sort.field());
            QueryFieldType fieldType = definition.type();
            Comparator<QueryRow> key = (left, right) -> QueryValueSemantics.compare(
                    left.cell(sort.field()), right.cell(sort.field()), fieldType);
            if (sort.direction() == QuerySortDirection.DESC) {
                key = key.reversed();
            }
            comparator = comparator.thenComparing(key);
        }
        return comparator
                .thenComparing(QueryRow::projectId)
                .thenComparing(QueryRow::entityId);
    }

    List<String> columns(QueryDefinition query) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        switch (query.entityType()) {
            case PORTFOLIO_MEMBERSHIP -> {
                columns.add("portfolioId");
                columns.add("projectId");
            }
            case PORTFOLIO_REFERENCE -> {
                columns.add("id");
                columns.add("portfolioId");
                columns.add("projectId");
            }
            default -> {
                columns.add("id");
                columns.add("projectId");
            }
        }
        if (query.projection().fields().isEmpty()) {
            columns.addAll(QuerySchemaRegistry.defaultProjection(query.entityType()));
        } else {
            columns.addAll(query.projection().fields());
        }
        return List.copyOf(columns);
    }

    private boolean matchesPredicate(QueryRow row, QueryPredicate predicate) {
        QueryCell cell = row.cell(predicate.field()).orElseGet(() -> new QueryCell(predicate.field(), List.of()));
        if (predicate.operator() == QueryOperator.EXISTS) {
            return !cell.values().isEmpty();
        }
        QueryFieldType fieldType = QuerySchemaRegistry.fields(row.entityType()).get(predicate.field()).type();
        return switch (predicate.operator()) {
            case EQ -> anyEqual(cell.values(), predicate.values().getFirst(), fieldType);
            case NEQ -> !anyEqual(cell.values(), predicate.values().getFirst(), fieldType);
            case CONTAINS -> textAny(cell.values(), predicate.values().getFirst(), TextMatch.CONTAINS);
            case STARTS_WITH -> textAny(cell.values(), predicate.values().getFirst(), TextMatch.STARTS_WITH);
            case ENDS_WITH -> textAny(cell.values(), predicate.values().getFirst(), TextMatch.ENDS_WITH);
            case IN -> predicate.values().stream().anyMatch(value -> anyEqual(cell.values(), value, fieldType));
            case EXISTS -> throw new IllegalStateException("handled above");
        };
    }

    private boolean anyEqual(List<String> values, String expected, QueryFieldType type) {
        return values.stream().anyMatch(value -> QueryValueSemantics.sameValue(value, expected, type));
    }

    private boolean textAny(List<String> values, String expected, TextMatch mode) {
        String needle = expected.toLowerCase(Locale.ROOT);
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(value -> switch (mode) {
            case CONTAINS -> value.contains(needle);
            case STARTS_WITH -> value.startsWith(needle);
            case ENDS_WITH -> value.endsWith(needle);
        });
    }

    private enum TextMatch {
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH
    }
}
