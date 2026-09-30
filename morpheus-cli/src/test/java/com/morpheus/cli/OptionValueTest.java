package com.morpheus.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionValueTest {

    /**
     * Each half of the definition refuses what the other lets through: U+2003 and U+3000 are white space for
     * {@code isBlank} and survive {@code trim}; U+0001 and U+0000 are emptied by {@code trim} and are not white space
     * for {@code isBlank}. A control character next to an em space is blank for neither whole-string test, and is
     * refused because the definition decides per character.
     */
    @Test
    void aValueEitherTestCallsBlankIsRefusedNamingTheOption() {
        for (String value : List.of("", " ", "\t", "\u2003", "\u3000", " \u2003\t", "\u0001", "\u0001 \u0000",
                "\u0001\u2003", "\u2003\u0001", "\u0001\u2003\u0001")) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> OptionValue.nonBlank("--project", value));

            assertEquals("--project requires a non-blank value; omit the option to leave it unset",
                    refused.getMessage());
        }
    }

    /**
     * A no-break space, an invisible character that is not white space, and a control character followed by text are
     * values: the refusal does not claim otherwise.
     */
    @Test
    void aNoBreakSpaceAnInvisibleCharacterOrAControlBeforeTextIsAValue() {
        for (String value : List.of("\u00A0", "\u2007", "\u202F", "\u200B", "\uFEFF", "\u0085", "\u007F",
                "\u0001 x")) {
            assertSame(value, OptionValue.nonBlank("--project", value));
        }
    }

    /** The value is returned as given, so a caller that trims keeps trimming and a path keeps its spelling. */
    @Test
    void aNonBlankValueIsReturnedUnchanged() {
        String padded = " a\u2003value ";
        String spaced = "my docs/proof one.md";

        assertSame(padded, OptionValue.nonBlank("--query", padded));
        assertEquals(Path.of(spaced), OptionValue.path("--file", spaced));
        assertTrue(OptionValue.path("--file", spaced).toString().contains("proof one.md"));
    }

    @Test
    void aPathMadeOfUnicodeSpaceOrControlCharactersIsRefusedBeforePathOf() {
        for (String value : List.of("\u2003", "\u0001")) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> OptionValue.path("--data-dir", value));

            assertTrue(refused.getMessage().startsWith("--data-dir requires a non-blank value"), refused.getMessage());
        }
    }
}
