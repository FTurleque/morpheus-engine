package com.morpheus.domain.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderIdTest {

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertEquals("openspec", new ProviderId("  openspec\n").value());
    }

    @Test
    void aBlankIdentifierIsRefused() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> new ProviderId(" \t "));

        assertEquals("provider id must not be blank", refusal.getMessage());
    }

    @Test
    void anEmbeddedLineBreakIsRefusedAndNamedEscaped() {
        IllegalArgumentException refusal =
                assertThrows(IllegalArgumentException.class, () -> new ProviderId("openspec\nmarkdown"));

        assertEquals("provider id must not contain control characters: \"openspec\\u000Amarkdown\"",
                refusal.getMessage());
    }

    @Test
    void everyControlCharacterIsRefusedWhereverTrimmingDoesNotRemoveIt() {
        for (char control : new char[] {'\u0000', '\t', '\r', '\u001F', '\u007F', '\u0085', '\u009F'}) {
            String value = "open" + control + "spec";
            IllegalArgumentException refusal =
                    assertThrows(IllegalArgumentException.class, () -> new ProviderId(value), value);

            assertEquals("provider id must not contain control characters: \"open\\u%04Xspec\"".formatted((int) control),
                    refusal.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("openspec\u007F"));
    }

    @Test
    void printableIdentifiersAreAccepted() {
        for (String value : new String[] {"openspec", "structured-markdown", "reference-plugin", "vendor.plugin_2", "é"}) {
            assertEquals(value, new ProviderId(value).value());
        }
    }
}
