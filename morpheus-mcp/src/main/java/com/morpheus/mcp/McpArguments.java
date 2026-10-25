package com.morpheus.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The one rule for reading an MCP tool argument (ADR-0102).
 *
 * <p>Every tool class used to carry its own copy of this reading: ten definitions of {@code requiredString},
 * six of {@code optionalString}, nine numeric readers. They had already diverged into five different sentences
 * for the identical fault, and nothing detected it because no test called a handler.</p>
 *
 * <p>The recipient of these messages is an agent that has to repair its own call, so a message names the
 * offending argument first and then what was expected, and it distinguishes an omission from a malformed
 * value -- the repair is not the same one:</p>
 *
 * <pre>
 * absent                            name is required and must be &lt;expectation&gt;
 * present but unusable              name must be &lt;expectation&gt;
 * optional, present but unusable    name must be &lt;expectation&gt; when present
 * </pre>
 */
final class McpArguments {
    private static final String STRING = "a string";
    private static final String NON_BLANK_STRING = "a non-blank string";
    private static final String INTEGER = "an integer";
    private static final String BOOLEAN = "a boolean";
    private static final String FINITE_NUMBER = "a finite number";
    private static final String WHEN_PRESENT = " when present";

    private McpArguments() {
    }

    /** MCP delivers no arguments as a null map; every reader below expects a map it can query. */
    static Map<String, Object> orEmpty(Map<String, Object> arguments) {
        return arguments == null ? Map.of() : arguments;
    }

    static String requiredString(Map<String, Object> arguments, String name) {
        Object value = value(arguments, name);
        if (value == null) {
            throw missing(name, NON_BLANK_STRING);
        }
        return nonBlankString(value, name, "");
    }

    static Optional<String> optionalString(Map<String, Object> arguments, String name) {
        Object value = value(arguments, name);
        return value == null ? Optional.empty() : Optional.of(nonBlankString(value, name, WHEN_PRESENT));
    }

    /**
     * A string the schema allows to be empty, such as {@code find_requirements}'s {@code query}: it declares
     * {@code type: string} without a {@code minLength}, and a blank query legitimately matches everything.
     * Reading it as a non-blank string would refuse an input the published schema accepts.
     */
    static Optional<String> optionalText(Map<String, Object> arguments, String name) {
        Object value = value(arguments, name);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String text)) {
            throw unusable(name, STRING, WHEN_PRESENT);
        }
        return Optional.of(text.trim());
    }

    static boolean requiredBoolean(Map<String, Object> arguments, String name) {
        Object value = value(arguments, name);
        if (value == null) {
            throw missing(name, BOOLEAN);
        }
        if (!(value instanceof Boolean flag)) {
            throw unusable(name, BOOLEAN, "");
        }
        return flag;
    }

    static boolean optionalBoolean(Map<String, Object> arguments, String name, boolean defaultValue) {
        Object value = value(arguments, name);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean flag)) {
            throw unusable(name, BOOLEAN, WHEN_PRESENT);
        }
        return flag;
    }

    static long requiredInteger(Map<String, Object> arguments, String name) {
        return requiredInteger(arguments, name, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    static long requiredInteger(Map<String, Object> arguments, String name, long minimum, long maximum) {
        Object value = value(arguments, name);
        if (value == null) {
            throw missing(name, INTEGER);
        }
        return integral(value, name, minimum, maximum, "");
    }

    static long optionalInteger(Map<String, Object> arguments, String name, long defaultValue) {
        return optionalInteger(arguments, name, defaultValue, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    static long optionalInteger(
            Map<String, Object> arguments, String name, long defaultValue, long minimum, long maximum) {
        Object value = value(arguments, name);
        return value == null ? defaultValue : integral(value, name, minimum, maximum, WHEN_PRESENT);
    }

    static int optionalInt(Map<String, Object> arguments, String name, int defaultValue) {
        return Math.toIntExact(optionalInteger(arguments, name, defaultValue));
    }

    static int optionalInt(
            Map<String, Object> arguments, String name, int defaultValue, int minimum, int maximum) {
        return Math.toIntExact(optionalInteger(arguments, name, defaultValue, minimum, maximum));
    }

    static double requiredFiniteNumber(Map<String, Object> arguments, String name) {
        Object value = value(arguments, name);
        if (value == null) {
            throw missing(name, FINITE_NUMBER);
        }
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw unusable(name, FINITE_NUMBER, "");
        }
        return number.doubleValue();
    }

    /** An absent array reads as empty: the schema decides whether the argument was required at all. */
    static List<String> stringList(Map<String, Object> arguments, String name) {
        Object value = value(arguments, name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> items)) {
            throw new IllegalArgumentException(name + " must be an array of strings");
        }
        List<String> result = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(name + " must contain non-blank strings");
            }
            result.add(text.trim());
        }
        return List.copyOf(result);
    }

    static Map<String, String> stringMap(Map<String, Object> arguments, String name) {
        Object value = value(arguments, name);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> entries)) {
            throw new IllegalArgumentException(name + " must be an object of string values");
        }
        Map<String, String> result = new LinkedHashMap<>();
        entries.forEach((key, entry) -> {
            if (!(key instanceof String textKey) || textKey.isBlank()
                    || !(entry instanceof String textValue) || textValue.isBlank()) {
                throw new IllegalArgumentException(name + " must contain non-blank string keys and values");
            }
            result.put(textKey.trim(), textValue.trim());
        });
        return Map.copyOf(result);
    }

    /**
     * Nested objects arrive with unconstrained key types, so they are narrowed before being read as arguments.
     * The name is that of the enclosing argument, which is the one the caller can act on.
     */
    static Map<String, Object> nestedObject(Object raw, String name) {
        if (!(raw instanceof Map<?, ?> entries)) {
            throw new IllegalArgumentException(name + " must contain objects");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        entries.forEach((key, value) -> {
            if (!(key instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(name + " keys must be non-blank strings");
            }
            result.put(text, value);
        });
        return Map.copyOf(result);
    }

    private static Object value(Map<String, Object> arguments, String name) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(name, "name");
        return arguments.get(name);
    }

    private static String nonBlankString(Object value, String name, String qualifier) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw unusable(name, NON_BLANK_STRING, qualifier);
        }
        return text.trim();
    }

    private static long integral(Object value, String name, long minimum, long maximum, String qualifier) {
        if (!(value instanceof Number number)) {
            throw unusable(name, INTEGER, qualifier);
        }
        long integral = number.longValue();
        if (Double.compare(number.doubleValue(), (double) integral) != 0) {
            throw unusable(name, INTEGER, qualifier);
        }
        if (integral < minimum || integral > maximum) {
            throw unusable(name, INTEGER + " between " + minimum + " and " + maximum, qualifier);
        }
        return integral;
    }

    private static IllegalArgumentException missing(String name, String expectation) {
        return new IllegalArgumentException(name + " is required and must be " + expectation);
    }

    private static IllegalArgumentException unusable(String name, String expectation, String qualifier) {
        return new IllegalArgumentException(name + " must be " + expectation + qualifier);
    }
}
