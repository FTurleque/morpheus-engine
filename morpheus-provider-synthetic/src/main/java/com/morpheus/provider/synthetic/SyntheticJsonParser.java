package com.morpheus.provider.synthetic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON parser scoped to the verification-only synthetic provider. */
final class SyntheticJsonParser {
    private final String input;
    private int index;

    private SyntheticJsonParser(String input) {
        this.input = input;
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
        Object value = parseValue();
        skipWhitespace();
        if (index != input.length()) {
            throw error("unexpected trailing content");
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (index >= input.length()) {
            throw error("unexpected end of input");
        }
        return switch (input.charAt(index)) {
            case '{' -> parseObjectValue();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObjectValue() {
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
            Object value = parseValue();
            result.put(key, value);
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            expect(',');
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (peek(']')) {
            index++;
            return result;
        }
        while (true) {
            result.add(parseValue());
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
                result.append(current);
                continue;
            }
            if (index >= input.length()) {
                throw error("unterminated escape sequence");
            }
            char escaped = input.charAt(index++);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> result.append(parseUnicodeEscape());
                default -> throw error("unsupported escape sequence: \\" + escaped);
            }
        }
        throw error("unterminated string");
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
            return decimal ? Double.parseDouble(token) : Long.parseLong(token);
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
