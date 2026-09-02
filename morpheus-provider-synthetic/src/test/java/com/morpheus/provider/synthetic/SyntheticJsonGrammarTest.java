package com.morpheus.provider.synthetic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the parser to the JSON grammar rather than to what the platform happens to decode.
 *
 * <p>The bounds on size, depth, node count and string length are a separate concern and stay as they were. What
 * these cover is acceptance: a document either is JSON or it is refused, so two sources cannot disagree in text
 * and agree in the parsed model.</p>
 */
class SyntheticJsonGrammarTest {
    @Test
    void numbersOutsideTheJsonGrammarAreRefused() {
        List<String> invalid = List.of(
                "01", "00", "-01", "1.", ".1", "1e", "1e+", "1e-", "+1", "-",
                "1.2.3", "0x1F", "١٢");

        for (String literal : invalid) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SyntheticJsonParser.parseObject("{\"value\":" + literal + "}"),
                    () -> "must refuse the number literal: " + literal);
        }
    }

    @Test
    void theJsonNumbersThatAreValidStillParse() {
        assertEquals(0L, value("0"));
        assertEquals(-1L, value("-1"));
        assertEquals(10L, value("10"));
        assertEquals(0.5d, value("0.5"));
        assertEquals(-0.5d, value("-0.5"));
        assertEquals(1000.0d, value("1e3"));
        assertEquals(1000.0d, value("1E+3"));
        assertEquals(0.001d, value("1e-3"));
        assertEquals(1.25d, value("1.25"));
    }

    /**
     * Character.isDigit is true for every Unicode decimal digit and Long.parseLong decodes them, so this used to
     * parse as the number three -- the same value reachable through a document JSON does not allow.
     */
    @Test
    void unicodeDigitsAreNotJsonDigits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"value\":\u0663}"));
    }

    @Test
    void rawControlCharactersInsideAStringAreRefused() {
        for (char control : new char[] {0x00, 0x01, 0x09, 0x0A, 0x0D, 0x1F}) {
            String document = "{\"value\":\"a" + control + "b\"}";
            IllegalArgumentException refused = assertThrows(
                    IllegalArgumentException.class,
                    () -> SyntheticJsonParser.parseObject(document),
                    () -> "must refuse raw control character " + (int) control);
            assertTrue(refused.getMessage().contains("control character"), refused.getMessage());
        }
    }

    @Test
    void theEscapedFormsOfThoseCharactersAreAccepted() {
        assertEquals("a\tb", value("\"a\\tb\""));
        assertEquals("a\nb", value("\"a\\nb\""));
        assertEquals("a" + (char) 0x01 + "b", value("\"a\\u0001b\""));
    }

    @Test
    void unicodeEscapesMustBeFourHexDigits() {
        List<String> invalid = List.of(
                "\"\\u\"",
                "\"\\u12\"",
                "\"\\u12g4\"",
                "\"\\u+123\"",
                "\"\\u 123\"",
                "\"\\u" + (char) 0x0663 + "123\"");

        for (String literal : invalid) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SyntheticJsonParser.parseObject("{\"value\":" + literal + "}"),
                    () -> "must refuse the escape: " + literal);
        }
    }

    /** An unpaired surrogate cannot round-trip through UTF-8, so it must not enter the parsed model. */
    @Test
    void unpairedSurrogateEscapesAreRefusedAndPairsAreAccepted() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"value\":\"\\uD83D\"}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"value\":\"\\uDE00\"}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"value\":\"\\uD83D\\u0041\"}"));

        assertEquals(
                new String(Character.toChars(0x1F600)),
                value("\"\\uD83D\\uDE00\""));
    }

    /**
     * code-style forbids a silent last-write-wins. A duplicate key is a conflict in the source: whichever value
     * survived would depend on document order rather than on the data.
     */
    @Test
    void duplicateObjectKeysAreRefusedRatherThanOverwritten() {
        IllegalArgumentException refused = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"id\":\"first\",\"id\":\"second\"}"));
        assertTrue(refused.getMessage().contains("duplicate JSON object key: id"), refused.getMessage());

        assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"outer\":{\"k\":1,\"k\":2}}"));
    }

    @Test
    void distinctKeysAndNestedStructuresStillParse() {
        Map<String, Object> parsed = SyntheticJsonParser.parseObject(
                "{\"a\":1,\"b\":[1,2,{\"c\":true}],\"d\":null}");

        assertEquals(1L, parsed.get("a"));
        assertEquals(List.of(1L, 2L, Map.of("c", Boolean.TRUE)), parsed.get("b"));
        assertTrue(parsed.containsKey("d"));
        assertEquals(null, parsed.get("d"));
    }

    private static Object value(String jsonLiteral) {
        return SyntheticJsonParser.parseObject("{\"value\":" + jsonLiteral + "}").get("value");
    }
}
