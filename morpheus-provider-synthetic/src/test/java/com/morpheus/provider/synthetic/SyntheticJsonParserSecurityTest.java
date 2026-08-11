package com.morpheus.provider.synthetic;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticJsonParserSecurityTest {

    @Test
    void acceptsMaximumDepthAndRejectsDepthPlusOneWithoutStackOverflow() {
        String accepted = nestedArrayDocument(SyntheticJsonParser.MAX_DEPTH - 2);
        assertDoesNotThrow(() -> SyntheticJsonParser.parseObject(accepted));

        String rejected = nestedArrayDocument(SyntheticJsonParser.MAX_DEPTH - 1);
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject(rejected));
        assertTrue(failure.getMessage().contains("maximum nesting depth"));
    }

    @Test
    void boundsNodeCardinality() {
        int acceptedElements = SyntheticJsonParser.MAX_NODES - 2;
        assertDoesNotThrow(() -> SyntheticJsonParser.parseObject(arrayDocument(acceptedElements)));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject(arrayDocument(acceptedElements + 1)));
        assertTrue(failure.getMessage().contains("maximum node count"));
    }

    @Test
    void boundsStringLength() {
        String accepted = "a".repeat(SyntheticJsonParser.MAX_STRING_CHARS);
        Map<String, Object> parsed = SyntheticJsonParser.parseObject("{\"value\":\"" + accepted + "\"}");
        assertTrue(parsed.containsKey("value"));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject(
                        "{\"value\":\"" + "a".repeat(SyntheticJsonParser.MAX_STRING_CHARS + 1) + "\"}"));
        assertTrue(failure.getMessage().contains("maximum length"));
    }

    @Test
    void rejectsOversizedUtf8InputBeforeParsing() {
        String oversized = "{" + " ".repeat(SyntheticJsonParser.MAX_INPUT_BYTES) + "}";
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject(oversized));
        assertTrue(failure.getMessage().contains("maximum input size"));
    }

    @Test
    void rejectsNonFiniteNumbers() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"value\":1e999}"));
        assertTrue(failure.getMessage().contains("must be finite"));
    }

    private static String nestedArrayDocument(int arrays) {
        return "{\"value\":" + "[".repeat(arrays) + "0" + "]".repeat(arrays) + "}";
    }

    private static String arrayDocument(int elements) {
        StringBuilder json = new StringBuilder(elements * 2 + 16).append("{\"value\":[");
        for (int index = 0; index < elements; index++) {
            if (index > 0) json.append(',');
            json.append('0');
        }
        return json.append("]}").toString();
    }
}
