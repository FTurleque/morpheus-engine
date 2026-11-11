package com.morpheus.application.foundation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the string-keyed map a public answer or an MCP tool schema is serialised from, out of alternating keys and
 * values.
 *
 * <p>A malformed construction is refused by name: an odd number of arguments, a key that is not a {@code String}
 * (with its position and the type received) and a key given twice (with the key; neither value is kept). Whether an
 * absent value is admitted is chosen at the call: {@link #of} refuses it with its key, {@link #admittingAbsentValues}
 * keeps it, and the canonical serialisation writes it as JSON {@code null}.</p>
 *
 * <p>The map returned refuses modification and iterates its entries in the order they were given, on every run.</p>
 */
public final class PayloadMap {

    private PayloadMap() {
    }

    /** A payload whose every value is present. */
    public static Map<String, Object> of(Object... entries) {
        return build(entries, false);
    }

    /** A payload in which a value may be absent, as the side of a difference that has no source. */
    public static Map<String, Object> admittingAbsentValues(Object... entries) {
        return build(entries, true);
    }

    private static Map<String, Object> build(Object[] entries, boolean absentValuesAdmitted) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "payload map entries must be key/value pairs; received " + entries.length + " arguments");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            if (!(entries[index] instanceof String key)) {
                throw new IllegalArgumentException("payload map key at position " + index + " must be a String; received "
                        + (entries[index] == null ? "null" : entries[index].getClass().getName()));
            }
            Object value = entries[index + 1];
            if (value == null && !absentValuesAdmitted) {
                throw new IllegalArgumentException("payload map value of key " + key + " must be present");
            }
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("payload map key given twice: " + key);
            }
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }
}
