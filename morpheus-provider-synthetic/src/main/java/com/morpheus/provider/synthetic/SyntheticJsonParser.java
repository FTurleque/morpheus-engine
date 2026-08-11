package com.morpheus.provider.synthetic;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Small dependency-free JSON parser scoped to the verification-only synthetic provider. */
final class SyntheticJsonParser {
    static final int MAX_INPUT_BYTES = 1_048_576;
    static final int MAX_DEPTH = 64;
    static final int MAX_NODES = 100_000;
    static final int MAX_STRING_CHARS = 65_536;

    private final String input;
    private int index;
    private int nodes;

    private SyntheticJsonParser(String input) {
        this.input = Objects.requireNonNull(input, "input");
        int utf8Bytes = input.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException(
                    "synthetic JSON exceeds maximum input size of " + MAX_INPUT_BYTES + " UTF-8 bytes");
        }
    }

    static Map<String, Object> parseObject(String input) {
        Object value = new SyntheticJsonParser(input).parseDocument();
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("synthetic source root must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }

    private Object parseDocument() {
        skipWhitespace();
        Object value = parseValue(1);
        skipWhitespace();
        if (index != input.length()) {
            throw error("unexpected trailing content");
        }
        return value;
    }

    private Object parseValue(int depth) {
        guardNode(depth);
        skipWhitespace();
        if (index >= input.length()) {
            throw error("unexpected end of input");
        }
        return switch (input.charAt(index)) {
            case '{' -> parseObjectValue(depth);
            case '[' -> parseArray(depth);
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObjectValue(int depth) {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek('}')) {
            index++;
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue(depth + 1);
            result.put(key, value);
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            expect(',');
        }
    }

    private List<Object> parseArray(int depth) {
        expect('[');
        List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (peek(']')) {
            index++;
            return result;
        }
        while (true) {
            result.add(parseValue(depth + 1));
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            expect(',');
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (index < input.length()) {
            char current = input.charAt(index++);
            if (current == '"') {
                return result.toString();
            }
            if (current != '\\') {
                appendStringChar(result, current);
                continue;
            }
            if (index >= input.length()) {
                throw error("unterminated escape sequence");
            }
            char escaped = input.charAt(index++);
            switch (escaped) {
                case '"', '\\', '/' -> appendStringChar(result, escaped);
                case 'b' -> appendStringChar(result, '\b');
                case 'f' -> appendStringChar(result, '\f');
                case 'n' -> appendStringChar(result, '\n');
                case 'r' -> appendStringChar(result, '\r');
                case 't' -> appendStringChar(result, '\t');
                case 'u' -> appendStringChar(result, parseUnicodeEscape());
                default -> throw error("unsupported escape sequence: \\" + escaped);
            }
        }
        throw error("unterminated string");
    }

    private void appendStringChar(StringBuilder result, char value) {
        if (result.length() >= MAX_STRING_CHARS) {
            throw error("synthetic JSON string exceeds maximum length of " + MAX_STRING_CHARS + " characters");
        }
        result.append(value);
    }

    private char parseUnicodeEscape() {
        if (index + 4 > input.length()) {
            throw error("incomplete unicode escape");
        }
        String hex = input.substring(index, index + 4);
        index += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException exception) {
            throw error("invalid unicode escape: " + hex);
        }
    }

    private Object parseNumber() {
        int start = index;
        if (peek('-')) {
            index++;
        }
        while (index < input.length() && Character.isDigit(input.charAt(index))) {
            index++;
        }
        boolean decimal = false;
        if (peek('.')) {
            decimal = true;
            index++;
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
        }
        if (peek('e') || peek('E')) {
            decimal = true;
            index++;
            if (peek('+') || peek('-')) {
                index++;
            }
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
        }
        if (start == index) {
            throw error("expected JSON value");
        }
        String token = input.substring(start, index);
        try {
            if (decimal) {
                double value = Double.parseDouble(token);
                if (!Double.isFinite(value)) throw error("JSON number must be finite");
                return value;
            }
            return Long.parseLong(token);
        } catch (NumberFormatException exception) {
            throw error("invalid number: " + token);
        }
    }

    private Object parseLiteral(String literal, Object value) {
        if (!input.startsWith(literal, index)) {
            throw error("expected " + literal);
        }
        index += literal.length();
        return value;
    }

    private void guardNode(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("synthetic JSON exceeds maximum nesting depth of " + MAX_DEPTH);
        }
        nodes++;
        if (nodes > MAX_NODES) {
            throw error("synthetic JSON exceeds maximum node count of " + MAX_NODES);
        }
    }

    private void skipWhitespace() {
        while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
    }

    private void expect(char expected) {
        skipWhitespace();
        if (index >= input.length() || input.charAt(index) != expected) {
            throw error("expected '" + expected + "'");
        }
        index++;
    }

    private boolean peek(char expected) {
        return index < input.length() && input.charAt(index) == expected;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at index " + index);
    }
}
