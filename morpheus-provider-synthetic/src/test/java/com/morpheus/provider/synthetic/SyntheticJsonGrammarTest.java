package com.morpheus.provider.synthetic;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
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
        Map<String, String> invalid = new LinkedHashMap<>();
        invalid.put("01", "JSON number must not have a leading zero: 01");
        invalid.put("00", "JSON number must not have a leading zero: 00");
        invalid.put("-01", "JSON number must not have a leading zero: -01");
        invalid.put("1.", "JSON number requires at least one digit after the decimal point");
        invalid.put(".1", "expected JSON value");
        invalid.put("1e", "JSON number requires at least one digit in its exponent");
        invalid.put("1e+", "JSON number requires at least one digit in its exponent");
        invalid.put("1e-", "JSON number requires at least one digit in its exponent");
        invalid.put("+1", "expected JSON value");
        invalid.put("-", "expected JSON value");
        invalid.put("1.2.3", "expected ','");
        invalid.put("0x1F", "expected ','");
        invalid.put("١٢", "expected JSON value");

        invalid.forEach((literal, message) -> {
            IllegalArgumentException refused = assertThrows(
                    IllegalArgumentException.class,
                    () -> SyntheticJsonParser.parseObject("{\"value\":" + literal + "}"),
                    () -> "must refuse the number literal: " + literal);
            assertTrue(refused.getMessage().startsWith(message + " at index "),
                    () -> literal + " -> " + refused.getMessage());
        });
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
        IllegalArgumentException refused = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"value\":\u0663}"));
        assertTrue(refused.getMessage().startsWith("expected JSON value at index "), refused.getMessage());
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
        Map<String, String> invalid = new LinkedHashMap<>();
        invalid.put("\"\\u\"", "incomplete unicode escape");
        invalid.put("\"\\u12\"", "invalid unicode escape: 12\"}");
        invalid.put("\"\\u12g4\"", "invalid unicode escape: 12g4");
        invalid.put("\"\\u+123\"", "invalid unicode escape: +123");
        invalid.put("\"\\u 123\"", "invalid unicode escape:  123");
        invalid.put("\"\\u" + (char) 0x0663 + "123\"", "invalid unicode escape: " + (char) 0x0663 + "123");

        invalid.forEach((literal, message) -> {
            IllegalArgumentException refused = assertThrows(
                    IllegalArgumentException.class,
                    () -> SyntheticJsonParser.parseObject("{\"value\":" + literal + "}"),
                    () -> "must refuse the escape: " + literal);
            assertTrue(refused.getMessage().startsWith(message + " at index "),
                    () -> literal + " -> " + refused.getMessage());
        });
    }

    /** An unpaired surrogate cannot round-trip through UTF-8, so it must not enter the parsed model. */
    @Test
    void unpairedSurrogateEscapesAreRefusedAndPairsAreAccepted() {
        IllegalArgumentException loneHigh = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"value\":\"\\uD83D\"}"));
        assertTrue(loneHigh.getMessage().startsWith(
                        "high surrogate escape must be followed by a low surrogate escape at index "),
                loneHigh.getMessage());
        IllegalArgumentException loneLow = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"value\":\"\\uDE00\"}"));
        assertTrue(loneLow.getMessage().startsWith("unpaired low surrogate in unicode escape at index "),
                loneLow.getMessage());
        IllegalArgumentException highThenPlain = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"value\":\"\\uD83D\\u0041\"}"));
        assertTrue(highThenPlain.getMessage().startsWith(
                        "high surrogate escape must be followed by a low surrogate escape at index "),
                highThenPlain.getMessage());

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

        IllegalArgumentException nested = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{\"outer\":{\"k\":1,\"k\":2}}"));
        assertTrue(nested.getMessage().contains("duplicate JSON object key: k"), nested.getMessage());
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

    /**
     * RFC 8259 names four characters as whitespace between tokens. The parser skipped anything
     * {@code Character.isWhitespace} accepted, which is a much larger set, so documents that no conforming
     * reader would accept parsed here.
     */
    @Test
    void onlyTheFourJsonWhitespaceCharactersSeparateTokens() {
        for (String separator : List.of(" ", "\t", "\n", "\r", " \t\n\r ")) {
            String document = "{" + separator + "\"value\"" + separator + ":" + separator + "1" + separator + "}";
            assertEquals(1L, SyntheticJsonParser.parseObject(document).get("value"),
                    () -> "must accept JSON whitespace between tokens");
        }
    }

    /** Every one of these is Character.isWhitespace, and none of them is JSON whitespace. */
    @Test
    void unicodeSpacesThatAreNotJsonWhitespaceAreRefusedBetweenTokens() {
        for (char separator : new char[] {0x000B, 0x000C, 0x001C, 0x001D, 0x001E, 0x001F, 0x2028, 0x2029, 0x3000}) {
            String document = "{" + separator + "\"value\":1}";
            IllegalArgumentException refused = assertThrows(
                    IllegalArgumentException.class,
                    () -> SyntheticJsonParser.parseObject(document),
                    () -> "must refuse U+" + String.format("%04X", (int) separator) + " as a token separator");
            assertTrue(refused.getMessage().startsWith("expected '\"' at index "), refused.getMessage());
        }
    }

    /** A no-break space is not whitespace to Java either, but it is the separator a reader reaches for first. */
    @Test
    void aNoBreakSpaceIsNotATokenSeparator() {
        IllegalArgumentException refused = assertThrows(
                IllegalArgumentException.class,
                () -> SyntheticJsonParser.parseObject("{" + (char) 0x00A0 + "\"value\":1}"));
        assertTrue(refused.getMessage().startsWith("expected '\"' at index "), refused.getMessage());
    }

    /**
     * Both providers catch {@code IllegalArgumentException} and turn it into an {@code INVALID_SOURCE} diagnostic. A
     * truncated document that escaped as an index exception would crash the read instead of being reported.
     */
    @Test
    void truncatedDocumentsAreRefusedAsIllegalArgumentNamingWhatIsMissing() {
        Map<String, String> refusals = new LinkedHashMap<>();
        refusals.put("", "unexpected end of input");
        refusals.put("{", "expected '\"'");
        refusals.put("{\"value\"", "expected ':'");
        refusals.put("{\"value\":", "unexpected end of input");
        refusals.put("{\"value\":1", "expected ','");
        refusals.put("{\"value\":\"abc", "unterminated string");
        refusals.put("{\"value\":\"abc\\", "unterminated escape sequence");
        refusals.put("{\"value\":\"\\uD83D", "high surrogate escape must be followed by a low surrogate escape");
        refusals.put("{\"value\":\"\\uD83D\\", "high surrogate escape must be followed by a low surrogate escape");
        refusals.put("{\"value\":\"\\u0041", "unterminated string");
        refusals.forEach((document, message) -> {
            IllegalArgumentException refusal = assertThrows(
                    IllegalArgumentException.class, () -> SyntheticJsonParser.parseObject(document), document);
            assertTrue(refusal.getMessage().startsWith(message + " at index "),
                    () -> document + " -> " + refusal.getMessage());
        });
        IllegalArgumentException notAnObject = assertThrows(
                IllegalArgumentException.class, () -> SyntheticJsonParser.parseObject("1"));
        assertEquals("synthetic source root must be a JSON object", notAnObject.getMessage());
    }

    @Test
    void emptyContainersParseWithOrWithoutWhitespaceInside() {
        assertEquals(Map.of(), SyntheticJsonParser.parseObject("{}"));
        assertEquals(Map.of(), SyntheticJsonParser.parseObject("{\n}"));
        assertEquals(Map.of(), value("{}"));
        assertEquals(Map.of(), value("{ }"));
        assertEquals(List.of(), value("[]"));
        assertEquals(List.of(), value("[ ]"));
        assertEquals(List.of(), value("[\n]"));
    }

    @Test
    void everyJsonEscapeIsDecoded() {
        assertEquals("a\"b\\c/d", value("\"a\\\"b\\\\c\\/d\""));
        assertEquals("a\bb", value("\"a\\bb\""));
        assertEquals("a\fb", value("\"a\\fb\""));
        assertEquals("a\rb", value("\"a\\rb\""));
    }

    /** Each boundary of each hexadecimal digit range: {@code 9}, {@code a}, {@code f}, {@code A}, {@code F}. */
    @Test
    void unicodeEscapesAcceptEveryHexadecimalDigitRangeToItsBounds() {
        assertEquals("9", value("\"\\u0039\""));
        assertEquals(String.valueOf((char) 0x00AF), value("\"\\u00af\""));
        assertEquals(String.valueOf((char) 0x00AF), value("\"\\u00AF\""));
    }

    @Test
    void aRawCharacterOutsideTheBasicPlaneParsesInsideAString() {
        String emoji = new String(Character.toChars(0x1F600));

        assertEquals(emoji, value("\"" + emoji + "\""));
        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> SyntheticJsonParser.parseObject(emoji));
        assertTrue(refused.getMessage().startsWith("expected JSON value at index "), refused.getMessage());
    }

    private static Object value(String jsonLiteral) {
        return SyntheticJsonParser.parseObject("{\"value\":" + jsonLiteral + "}").get("value");
    }
}
