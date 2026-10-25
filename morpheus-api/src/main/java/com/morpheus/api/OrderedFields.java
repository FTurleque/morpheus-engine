package com.morpheus.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The key/value view an API service answers, in the order its fields are listed.
 *
 * <p>A {@link LinkedHashMap} keeps that order stable and accepts a JSON {@code null} value, which a historical
 * diff carries; the canonical serializer still sorts the keys when it writes them.</p>
 */
final class OrderedFields {
    private OrderedFields() {
    }

    static Map<String, Object> map(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("map entries must be key/value pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return Collections.unmodifiableMap(result);
    }
}
