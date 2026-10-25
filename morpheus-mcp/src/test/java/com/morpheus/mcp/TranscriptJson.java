package com.morpheus.mcp;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites one JSON document with the elements of every array in sorted order, keys and scalars untouched.
 *
 * <p>For a transcript whose arrays are ordered by generated identifiers: once those identifiers are named alike, two
 * runs that differ only by that order give the same text. The reader accepts what the canonical serializer writes
 * and refuses anything else, so a document it cannot read fails the comparison instead of being compared loosely.</p>
 */
final class TranscriptJson {
    private final String text;
    private int position;

    private TranscriptJson(String text) {
        this.text = text;
    }

    static String withSortedArrays(String json) {
        TranscriptJson reader = new TranscriptJson(json);
        String value = reader.value();
        if (reader.position != json.length()) {
            throw new IllegalArgumentException("trailing text after JSON at " + reader.position + ": " + json);
        }
        return value;
    }

    private String value() {
        char next = text.charAt(position);
        if (next == '{') {
            return object();
        }
        if (next == '[') {
            return array();
        }
        if (next == '"') {
            return string();
        }
        int start = position;
        while (position < text.length() && ",]}".indexOf(text.charAt(position)) < 0) {
            position++;
        }
        if (start == position) {
            throw new IllegalArgumentException("no JSON value at " + start + ": " + text);
        }
        return text.substring(start, position);
    }

    private String object() {
        StringBuilder result = new StringBuilder("{");
        position++;
        while (text.charAt(position) != '}') {
            result.append(string());
            expect(':');
            result.append(':').append(value());
            if (text.charAt(position) == ',') {
                position++;
                result.append(',');
            }
        }
        position++;
        return result.append('}').toString();
    }

    private String array() {
        List<String> elements = new ArrayList<>();
        position++;
        while (text.charAt(position) != ']') {
            elements.add(value());
            if (text.charAt(position) == ',') {
                position++;
            }
        }
        position++;
        return "[" + String.join(",", elements.stream().sorted().toList()) + "]";
    }

    private String string() {
        int start = position;
        expect('"');
        while (text.charAt(position) != '"') {
            position += text.charAt(position) == '\\' ? 2 : 1;
        }
        position++;
        return text.substring(start, position);
    }

    private void expect(char expected) {
        if (text.charAt(position) != expected) {
            throw new IllegalArgumentException("expected '" + expected + "' at " + position + ": " + text);
        }
        position++;
    }
}
