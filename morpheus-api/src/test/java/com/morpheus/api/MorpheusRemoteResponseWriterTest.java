package com.morpheus.api;

import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MorpheusRemoteResponseWriterTest {

    @Test
    void successEnvelopeKeepsVersionedDataContract() {
        MorpheusRemoteResponseWriter writer = new MorpheusRemoteResponseWriter(new TimedBoundedResponseWriter());
        Map<String, Object> data = Map.of("state", "READY");

        Map<String, Object> envelope = writer.success(data);

        assertEquals("v1", envelope.get("apiVersion"));
        assertEquals(data, envelope.get("data"));
        assertEquals(2, envelope.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorEnvelopeKeepsVersionedDetailsContract() {
        MorpheusRemoteResponseWriter writer = new MorpheusRemoteResponseWriter(new TimedBoundedResponseWriter());

        Map<String, Object> envelope = writer.error("FORBIDDEN", "denied");
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");

        assertEquals("v1", envelope.get("apiVersion"));
        assertEquals("FORBIDDEN", error.get("code"));
        assertEquals("denied", error.get("message"));
        assertEquals(Map.of(), error.get("details"));
        assertEquals(2, envelope.size());
        assertEquals(3, error.size());
    }

    @Test
    void securityHeadersRemainStrictRequestScopedAndCorsFree() {
        MorpheusRemoteResponseWriter writer = new MorpheusRemoteResponseWriter(new TimedBoundedResponseWriter());
        Headers headers = new Headers();

        writer.applySecurityHeaders(headers, "request-123");

        assertEquals("no-store", headers.getFirst("Cache-Control"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
        assertEquals("no-referrer", headers.getFirst("Referrer-Policy"));
        assertEquals("default-src 'none'; frame-ancestors 'none'", headers.getFirst("Content-Security-Policy"));
        assertEquals("request-123", headers.getFirst("X-Request-Id"));
        assertFalse(headers.containsKey("Access-Control-Allow-Origin"));
    }
}
