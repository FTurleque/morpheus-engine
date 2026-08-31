package com.morpheus.application.query.dsl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Type-aware equality and ordering shared by deterministic query execution. */
final class QueryValueSemantics {
    private QueryValueSemantics() {
    }

    static boolean sameValue(String actual, String expected, QueryFieldType type) {
        return switch (type) {
            case TEXT, ENUM, BOOLEAN -> actual.equalsIgnoreCase(expected);
            case IDENTITY -> actual.equals(expected);
            case NUMBER -> number(actual).compareTo(number(expected)) == 0;
        };
    }

    static int compare(Optional<QueryCell> left, Optional<QueryCell> right, QueryFieldType type) {
        if (type != QueryFieldType.NUMBER) {
            String leftKey = left.map(QueryCell::sortKey).orElse("");
            String rightKey = right.map(QueryCell::sortKey).orElse("");
            return leftKey.compareTo(rightKey);
        }
        return compareNumbers(left.map(QueryCell::values).orElseGet(List::of),
                right.map(QueryCell::values).orElseGet(List::of));
    }

    private static int compareNumbers(List<String> left, List<String> right) {
        int common = Math.min(left.size(), right.size());
        for (int index = 0; index < common; index++) {
            int compared = number(left.get(index)).compareTo(number(right.get(index)));
            if (compared != 0) return compared;
        }
        return Integer.compare(left.size(), right.size());
    }

    private static BigDecimal number(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid numeric query value: " + value, failure);
        }
    }
}
