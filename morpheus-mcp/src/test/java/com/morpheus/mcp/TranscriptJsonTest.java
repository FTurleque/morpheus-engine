package com.morpheus.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptJsonTest {

    @Test
    void anArrayOfObjectsIsSortedAndAnArrayOfScalarsKeepsItsOrder() {
        assertEquals("{\"links\":[{\"id\":\"a\"},{\"id\":\"b\"}],\"scope\":[\"z\",\"a\"],\"n\":1}",
                TranscriptJson.withObjectArraysSorted(
                        "{\"links\":[{\"id\":\"b\"},{\"id\":\"a\"}],\"scope\":[\"z\",\"a\"],\"n\":1}"));
    }

    @Test
    void anEscapedQuoteStaysInsideItsString() {
        assertEquals("{\"m\":\"say \\\"hi\\\", then go\",\"e\":[]}",
                TranscriptJson.withObjectArraysSorted("{\"m\":\"say \\\"hi\\\", then go\",\"e\":[]}"));
    }

    @Test
    void aDocumentItCannotReadIsRefused() {
        IllegalArgumentException missingComma = assertThrows(IllegalArgumentException.class,
                () -> TranscriptJson.withObjectArraysSorted("{\"a\":1\"b\":2}"));
        IllegalArgumentException trailing = assertThrows(IllegalArgumentException.class,
                () -> TranscriptJson.withObjectArraysSorted("{\"a\":1}x"));
        IllegalArgumentException truncated = assertThrows(IllegalArgumentException.class,
                () -> TranscriptJson.withObjectArraysSorted("{\"a\":[1,2"));

        assertTrue(missingComma.getMessage().startsWith("not a JSON value at 5"), missingComma.getMessage());
        assertTrue(trailing.getMessage().startsWith("trailing text after JSON at 7"), trailing.getMessage());
        assertTrue(truncated.getMessage().startsWith("unexpected end of JSON"), truncated.getMessage());
    }
}
