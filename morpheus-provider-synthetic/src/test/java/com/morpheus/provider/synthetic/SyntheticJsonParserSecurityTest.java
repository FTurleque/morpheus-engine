package com.morpheus.provider.synthetic;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void countsUtf8BytesRatherThanUtf16Characters() {
        String oversized = "{\"value\":\"" + "€".repeat(SyntheticJsonParser.MAX_INPUT_BYTES / 3) + "\"}";
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject(oversized));
        assertTrue(failure.getMessage().contains("UTF-8 bytes"));
    }

    @Test
    void aDocumentOfExactlyTheByteLimitIsAccepted() {
        assertEquals(Map.of(), SyntheticJsonParser.parseObject(
                "{" + " ".repeat(SyntheticJsonParser.MAX_INPUT_BYTES - 2) + "}"));
    }

    /**
     * U+007F is the last one-byte character and U+07FF the last two-byte one. Padded to exactly the limit, a document
     * built from either is accepted only if each is counted at its true width: one byte too many refuses it.
     */
    @Test
    void theLastCharacterOfEachUtf8WidthIsCountedAtThatWidth() {
        for (char last : new char[] {(char) 0x007F, (char) 0x07FF}) {
            String content = String.valueOf(last).repeat(SyntheticJsonParser.MAX_STRING_CHARS);
            String document = paddedToTheByteLimit("{\"value\":\"" + content + "\"", "}");

            assertEquals(content, SyntheticJsonParser.parseObject(document).get("value"),
                    () -> "U+" + Integer.toHexString(last));
        }
    }

    @Test
    void twoByteCharactersAreCountedBeforeTheStringLengthBound() {
        String oversized = "{\"value\":\"" + String.valueOf((char) 0x00E9).repeat(SyntheticJsonParser.MAX_INPUT_BYTES / 2)
                + "\"}";

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> SyntheticJsonParser.parseObject(oversized));
        assertTrue(failure.getMessage().contains("UTF-8 bytes"), failure.getMessage());
    }

    @Test
    void surrogatePairsAreCountedAsFourBytes() {
        String pair = "a" + new String(Character.toChars(0x1F600));
        String oversized = "{\"value\":\"" + pair.repeat(SyntheticJsonParser.MAX_INPUT_BYTES / 5 + 1) + "\"}";

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> SyntheticJsonParser.parseObject(oversized));
        assertTrue(failure.getMessage().contains("UTF-8 bytes"), failure.getMessage());
    }

    /**
     * Strict UTF-8 decoding upstream never yields a lone surrogate, so these hold for a direct call only: a lone
     * surrogate still counts as one byte, and a high surrogate at the very end is not read past.
     */
    @Test
    void loneSurrogatesAreCountedAndNeverReadPast() {
        String loneLows = String.valueOf((char) 0xDC00).repeat(SyntheticJsonParser.MAX_INPUT_BYTES + 1);
        IllegalArgumentException oversized = assertThrows(
                IllegalArgumentException.class, () -> SyntheticJsonParser.parseObject(loneLows));
        assertTrue(oversized.getMessage().contains("UTF-8 bytes"), oversized.getMessage());

        assertThrows(IllegalArgumentException.class, () -> SyntheticJsonParser.parseObject("{}" + (char) 0xD83D));
    }

    @Test
    void rejectsNonFiniteNumbers() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"value\":1e999}"));
        assertTrue(failure.getMessage().contains("must be finite"));
    }

    private static String paddedToTheByteLimit(String head, String tail) {
        int bytes = (head + tail).getBytes(StandardCharsets.UTF_8).length;
        return head + " ".repeat(SyntheticJsonParser.MAX_INPUT_BYTES - bytes) + tail;
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
