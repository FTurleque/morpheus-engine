package com.morpheus.provider.synthetic;

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
        if (exceedsUtf8ByteLimit(input, MAX_INPUT_BYTES)) {
            throw new IllegalArgumentException(
                    "synthetic JSON exceeds maximum input size of " + MAX_INPUT_BYTES + " UTF-8 bytes");
        }
    }

    /** Counts UTF-8 bytes without allocating a second byte array and stops as soon as the limit is exceeded. */
    private static boolean exceedsUtf8ByteLimit(String value, int limit) {
        int bytes = 0;
        for (int offset = 0; offset < value.length(); offset++) {
            char current = value.charAt(offset);
            if (current <= 0x7f) {
                bytes++;
            } else if (current <= 0x7ff) {
                bytes += 2;
            } else if (Character.isHighSurrogate(current)
                    && offset + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(offset + 1))) {
                bytes += 4;
                offset++;
            } else if (Character.isSurrogate(current)) {
                bytes++;
            } else {
                bytes += 3;
            }
            if (bytes > limit) {
                return true;
            }
        }
        return false;
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
            // code-style forbids a silent last-write-wins: a duplicate key is a conflict in the source, and
            // whichever value survived would depend on document order rather than on the data.
            if (result.containsKey(key)) {
                throw error("duplicate JSON object key: " + key);
            }
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
            if (current < 0x20) {
                // JSON strings carry these escaped. A raw one means two different documents -- one with the byte,
                // one with its escape -- would parse to the same model.
                throw error("unescaped control character U+"
                        + String.format(java.util.Locale.ROOT, "%04X", (int) current) + " in string");
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
                case 'u' -> appendUnicodeEscape(result);
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

    /**
     * Reads one {@code \\uXXXX} escape and, for a high surrogate, the low surrogate that must follow it.
     *
     * <p>An unpaired surrogate is not a character. Accepting one would put a value in the parsed model that
     * cannot round-trip through UTF-8, so two documents that differ would canonicalize to the same bytes.</p>
     */
    private void appendUnicodeEscape(StringBuilder result) {
        char first = readFourHexDigits();
        if (Character.isLowSurrogate(first)) {
            throw error("unpaired low surrogate in unicode escape");
        }
        if (!Character.isHighSurrogate(first)) {
            appendStringChar(result, first);
            return;
        }
        if (index + 1 >= input.length() || input.charAt(index) != '\\' || input.charAt(index + 1) != 'u') {
            throw error("high surrogate escape must be followed by a low surrogate escape");
        }
        index += 2;
        char second = readFourHexDigits();
        if (!Character.isLowSurrogate(second)) {
            throw error("high surrogate escape must be followed by a low surrogate escape");
        }
        appendStringChar(result, first);
        appendStringChar(result, second);
    }

    /** {@code Integer.parseInt(hex, 16)} also accepts signs and non-ASCII digits; JSON allows neither. */
    private char readFourHexDigits() {
        if (index + 4 > input.length()) {
            throw error("incomplete unicode escape");
        }
        int value = 0;
        for (int offset = 0; offset < 4; offset++) {
            char digit = input.charAt(index + offset);
            int parsed = hexValue(digit);
            if (parsed < 0) {
                throw error("invalid unicode escape: " + input.substring(index, index + 4));
            }
            value = (value << 4) | parsed;
        }
        index += 4;
        return (char) value;
    }

    private static int hexValue(char digit) {
        if (digit >= '0' && digit <= '9') return digit - '0';
        if (digit >= 'a' && digit <= 'f') return digit - 'a' + 10;
        if (digit >= 'A' && digit <= 'F') return digit - 'A' + 10;
        return -1;
    }

    /**
     * Reads a number under the JSON grammar rather than under what {@code Long}/{@code Double} happen to accept.
     *
     * <p>Two kinds of leniency mattered here. {@code Character.isDigit} is true for every Unicode decimal digit,
     * and {@code Long.parseLong} decodes them, so an Arabic-Indic {@code ٣} parsed as 3 -- the same value
     * reachable through two very different documents. And a token only had to survive {@code parseDouble}, which
     * accepts {@code 1.} and {@code .1}, neither of which is JSON. Leading zeros passed for the same reason.</p>
     */
    private Object parseNumber() {
        int start = index;
        if (peek('-')) {
            index++;
        }
        int integerDigits = consumeAsciiDigits();
        if (integerDigits == 0) {
            throw error("expected JSON value");
        }
        if (integerDigits > 1 && input.charAt(start + (input.charAt(start) == '-' ? 1 : 0)) == '0') {
            throw error("JSON number must not have a leading zero: " + input.substring(start, index));
        }

        boolean decimal = false;
        if (peek('.')) {
            decimal = true;
            index++;
            if (consumeAsciiDigits() == 0) {
                throw error("JSON number requires at least one digit after the decimal point");
            }
        }
        if (peek('e') || peek('E')) {
            decimal = true;
            index++;
            if (peek('+') || peek('-')) {
                index++;
            }
            if (consumeAsciiDigits() == 0) {
                throw error("JSON number requires at least one digit in its exponent");
            }
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

    /** ASCII digits only: the JSON grammar has no other digit, whatever the platform is willing to decode. */
    private int consumeAsciiDigits() {
        int consumed = 0;
        while (index < input.length()) {
            char current = input.charAt(index);
            if (current < '0' || current > '9') {
                break;
            }
            index++;
            consumed++;
        }
        return consumed;
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

    /**
     * RFC 8259 allows exactly four characters between tokens. {@link Character#isWhitespace(char)} accepts far
     * more -- a no-break space, a line separator, a vertical tab -- so using it made this parser accept
     * documents no conforming JSON reader would, which is the opposite of what a strict grammar is for.
     */
    private static boolean isJsonWhitespace(char candidate) {
        return candidate == ' ' || candidate == '\t' || candidate == '\n' || candidate == '\r';
    }

    private void skipWhitespace() {
        while (index < input.length() && isJsonWhitespace(input.charAt(index))) {
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
