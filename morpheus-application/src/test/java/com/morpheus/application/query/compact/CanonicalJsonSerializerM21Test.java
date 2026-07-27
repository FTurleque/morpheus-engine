package com.morpheus.application.query.compact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

class CanonicalJsonSerializerM21Test {
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();

    @Test
    void serializesUriAsDeterministicAsciiString() {
        assertEquals(
                "\"https://example.invalid/releases/morpheus%201.0.zip\"",
                serializer.toJson(URI.create("https://example.invalid/releases/morpheus%201.0.zip")));
    }

    @Test
    void serializesUriComponentsInsideRecords() {
        assertEquals(
                "{\"artifact\":\"https://example.invalid/morpheus.zip\"}",
                serializer.toJson(new UriView(URI.create("https://example.invalid/morpheus.zip"))));
    }

    private record UriView(URI artifact) {
    }
}
