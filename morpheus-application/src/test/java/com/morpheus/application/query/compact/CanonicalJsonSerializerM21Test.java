package com.morpheus.application.query.compact;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.charset.StandardCharsets;
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

    @Test
    void utf8TransportSerializationEnforcesCeilingBeforeReturningPayload() {
        assertArrayEquals(
                "\"ok\"".getBytes(StandardCharsets.UTF_8),
                serializer.toUtf8("ok", 4));
        assertThrows(
                Utf8BoundedTextBuilder.LimitExceededException.class,
                () -> serializer.toUtf8("abc", 4));
    }

    private record UriView(URI artifact) {
    }
}
