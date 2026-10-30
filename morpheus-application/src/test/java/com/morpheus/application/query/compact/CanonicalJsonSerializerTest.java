package com.morpheus.application.query.compact;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Canonical JSON: every control character escaped, map keys sorted, and every value JSON cannot hold refused. */
class CanonicalJsonSerializerTest {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    record Pair(String name, Optional<Integer> count) {
    }

    @Test
    void everyCharacterJsonReservesIsEscaped() {
        String encoded = json.toJson("q\" b\\ \b \f \n \r \t \u0001 \uD800");

        assertEquals("\"q\\\" b\\\\ \\b \\f \\n \\r \\t \\u0001 \\uD800\"", encoded);
    }

    @Test
    void recordsMapsArraysAndOptionalsAreWrittenInCanonicalOrder() {
        Map<String, Object> map = new TreeMap<>();
        map.put("b", new int[]{1, 2});
        map.put("a", List.of('x', true));

        assertEquals("{\"a\":[\"x\",true],\"b\":[1,2]}", json.toJson(map));
        assertEquals("{\"name\":\"n\",\"count\":null}", json.toJson(new Pair("n", Optional.empty())));
    }

    @Test
    void aValueJsonCannotHoldIsRefusedByName() {
        IllegalArgumentException nan = assertThrows(IllegalArgumentException.class, () -> json.toJson(Double.NaN));
        IllegalArgumentException infinite = assertThrows(IllegalArgumentException.class,
                () -> json.toJson(Float.POSITIVE_INFINITY));
        IllegalArgumentException key = assertThrows(IllegalArgumentException.class, () -> json.toJson(Map.of(1, "x")));
        IllegalArgumentException type = assertThrows(IllegalArgumentException.class, () -> json.toJson(new Object()));

        assertEquals("non-finite double is not valid JSON", nan.getMessage());
        assertEquals("non-finite float is not valid JSON", infinite.getMessage());
        assertEquals("canonical JSON maps require String keys", key.getMessage());
        assertEquals("unsupported canonical JSON type: java.lang.Object", type.getMessage());
    }
}
