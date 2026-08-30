package com.morpheus.application.query.dsl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryValueSemanticsTest {

    @Test
    void numericEqualityUsesNumericValueRatherThanTextRepresentation() {
        assertTrue(QueryValueSemantics.equal("1.00", "01.0", QueryFieldType.NUMBER));
        assertTrue(QueryValueSemantics.equal("Alpha", "alpha", QueryFieldType.TEXT));
        assertEquals(false, QueryValueSemantics.equal("ABC", "abc", QueryFieldType.IDENTITY));
    }

    @Test
    void numericSortOrdersNumbersNumericallyInsteadOfLexicographically() {
        Optional<QueryCell> two = Optional.of(new QueryCell("value", List.of("2")));
        Optional<QueryCell> ten = Optional.of(new QueryCell("value", List.of("10")));

        assertTrue(QueryValueSemantics.compare(two, ten, QueryFieldType.NUMBER) < 0);
        assertTrue(QueryValueSemantics.compare(ten, two, QueryFieldType.NUMBER) > 0);
        assertEquals(0, QueryValueSemantics.compare(
                Optional.of(new QueryCell("value", List.of("1.0"))),
                Optional.of(new QueryCell("value", List.of("1.00"))),
                QueryFieldType.NUMBER));
    }

    @Test
    void nonNumericSortKeepsExistingCaseInsensitiveTextKeySemantics() {
        Optional<QueryCell> left = Optional.of(new QueryCell("value", List.of("Alpha")));
        Optional<QueryCell> right = Optional.of(new QueryCell("value", List.of("beta")));

        assertTrue(QueryValueSemantics.compare(left, right, QueryFieldType.TEXT) < 0);
        assertTrue(QueryValueSemantics.compare(Optional.empty(), left, QueryFieldType.TEXT) < 0);
    }

    @Test
    void invalidNumericValueFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> QueryValueSemantics.equal("not-a-number", "1", QueryFieldType.NUMBER));
    }
}
