package com.morpheus.application.query.compact;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Minimal deterministic JSON serializer for the typed compact-query DTO surface. */
public final class CanonicalJsonSerializer {

    public String toJson(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    public byte[] toUtf8(Object value) {
        return toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    private void append(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof Optional<?> optional) {
            append(out, optional.orElse(null));
            return;
        }
        if (value instanceof String string) {
            appendString(out, string);
            return;
        }
        if (value instanceof URI uri) {
            appendString(out, uri.toASCIIString());
            return;
        }
        if (value instanceof Character character) {
            appendString(out, character.toString());
            return;
        }
        if (value instanceof Boolean bool) {
            out.append(bool);
            return;
        }
        if (value instanceof Number number) {
            appendNumber(out, number);
            return;
        }
        if (value instanceof Enum<?> enumeration) {
            appendString(out, enumeration.name());
            return;
        }
        if (value instanceof Map<?, ?> map) {
            appendMap(out, map);
            return;
        }
        if (value instanceof Collection<?> collection) {
            appendCollection(out, collection);
            return;
        }
        if (value.getClass().isArray()) {
            appendArray(out, value);
            return;
        }
        if (value.getClass().isRecord()) {
            appendRecord(out, value);
            return;
        }
        throw new IllegalArgumentException("unsupported canonical JSON type: " + value.getClass().getName());
    }

    private void appendNumber(StringBuilder out, Number number) {
        Objects.requireNonNull(number, "number");
        if (number instanceof Double value && !Double.isFinite(value)) {
            throw new IllegalArgumentException("non-finite double is not valid JSON");
        }
        if (number instanceof Float value && !Float.isFinite(value)) {
            throw new IllegalArgumentException("non-finite float is not valid JSON");
        }
        out.append(number);
    }

    private void appendMap(StringBuilder out, Map<?, ?> map) {
        TreeMap<String, Object> entries = new TreeMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("canonical JSON maps require String keys");
            }
            entries.put(key, entry.getValue());
        }

        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            appendString(out, entry.getKey());
            out.append(':');
            append(out, entry.getValue());
        }
        out.append('}');
    }

    private void appendCollection(StringBuilder out, Collection<?> collection) {
        out.append('[');
        boolean first = true;
        for (Object item : collection) {
            if (!first) {
                out.append(',');
            }
            first = false;
            append(out, item);
        }
        out.append(']');
    }

    private void appendArray(StringBuilder out, Object array) {
        out.append('[');
        int length = Array.getLength(array);
        for (int index = 0; index < length; index++) {
            if (index > 0) {
                out.append(',');
            }
            append(out, Array.get(array, index));
        }
        out.append(']');
    }

    private void appendRecord(StringBuilder out, Object record) {
        out.append('{');
        RecordComponent[] components = record.getClass().getRecordComponents();
        for (int index = 0; index < components.length; index++) {
            if (index > 0) {
                out.append(',');
            }
            RecordComponent component = components[index];
            appendString(out, component.getName());
            out.append(':');
            try {
                var accessor = component.getAccessor();
                if (!accessor.canAccess(record) && !accessor.trySetAccessible()) {
                    throw new IllegalAccessException("record accessor is not accessible");
                }
                append(out, accessor.invoke(record));
            } catch (IllegalAccessException | InvocationTargetException | SecurityException exception) {
                throw new IllegalArgumentException(
                        "cannot read compact record component: " + record.getClass().getName() + "." + component.getName(),
                        exception);
            }
        }
        out.append('}');
    }

    private void appendString(StringBuilder out, String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20 || Character.isSurrogate(character)) {
                        appendUnicodeEscape(out, character);
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }

    private void appendUnicodeEscape(StringBuilder out, char character) {
        out.append("\\u");
        String hex = Integer.toHexString(character).toUpperCase(java.util.Locale.ROOT);
        out.append("0".repeat(4 - hex.length()));
        out.append(hex);
    }
}
