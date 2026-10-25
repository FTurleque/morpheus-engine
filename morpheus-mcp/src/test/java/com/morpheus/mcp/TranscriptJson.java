package com.morpheus.mcp;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites one JSON document with the elements of every array of objects in sorted order, everything else untouched.
 *
 * <p>For a transcript whose collections of entities are ordered by generated identifiers: once those identifiers are
 * named alike, two runs that differ only by that order give the same text. An array of scalars keeps its order, so a
 * list of values whose order the product fixes is still compared in order. The reader accepts what the canonical
 * serializer writes, without whitespace, and refuses anything else: a document it cannot read fails the comparison
 * instead of being compared loosely.</p>
 */
final class TranscriptJson {
    private final String text;
    private int position;

    private TranscriptJson(String text) {
        this.text = text;
    }

    static String withObjectArraysSorted(String json) {
        TranscriptJson reader = new TranscriptJson(json);
        String value = reader.rewriteValue();
        if (reader.position != json.length()) {
            throw new IllegalArgumentException("trailing text after JSON at " + reader.position + ": " + json);
        }
        return value;
    }

    private String rewriteValue() {
        char next = current();
        if (next == '{') {
            return rewriteObject();
        }
        if (next == '[') {
            return rewriteArray();
        }
        if (next == '"') {
            return readString();
        }
        int start = position;
        while (position < text.length() && ",]}".indexOf(text.charAt(position)) < 0) {
            position++;
        }
        String scalar = text.substring(start, position);
        if (!scalar.matches("true|false|null|-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) {
            throw new IllegalArgumentException("not a JSON value at " + start + ": " + text);
        }
        return scalar;
    }

    private String rewriteObject() {
        StringBuilder result = new StringBuilder("{");
        expect('{');
        boolean first = true;
        while (current() != '}') {
            if (!first) {
                expect(',');
                result.append(',');
            }
            result.append(readString());
            expect(':');
            result.append(':').append(rewriteValue());
            first = false;
        }
        expect('}');
        return result.append('}').toString();
    }

    private String rewriteArray() {
        List<String> elements = new ArrayList<>();
        expect('[');
        while (current() != ']') {
            if (!elements.isEmpty()) {
                expect(',');
            }
            elements.add(rewriteValue());
        }
        expect(']');
        boolean objects = !elements.isEmpty() && elements.stream().allMatch(element -> element.startsWith("{"));
        return "[" + String.join(",", objects ? elements.stream().sorted().toList() : elements) + "]";
    }

    private String readString() {
        int start = position;
        expect('"');
        while (current() != '"') {
            position += current() == '\\' ? 2 : 1;
        }
        position++;
        return text.substring(start, position);
    }

    private char current() {
        if (position >= text.length()) {
            throw new IllegalArgumentException("unexpected end of JSON: " + text);
        }
        return text.charAt(position);
    }

    private void expect(char expected) {
        if (current() != expected) {
            throw new IllegalArgumentException("expected '" + expected + "' at " + position + ": " + text);
        }
        position++;
    }
}
