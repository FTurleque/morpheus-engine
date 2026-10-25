package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpResponseWriterArchitectureTest {

    @Test
    void localHttpResponseWritingStaysExtractedCanonicalAndHardened() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String writer = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpResponseWriter"));

        assertTrue(server.contains("private final MorpheusHttpResponseWriter responseWriter"));
        assertTrue(server.contains("responseWriter.send(exchange, status, body)"));
        assertFalse(server.contains("CanonicalJsonSerializer"));
        assertFalse(server.contains("com.sun.net.httpserver.Headers"));
        assertFalse(server.contains("headers.set(\"Content-Type\""));
        assertFalse(server.contains("headers.set(\"Cache-Control\""));
        assertFalse(server.contains("headers.set(\"X-Content-Type-Options\""));

        assertTrue(writer.contains("final class MorpheusHttpResponseWriter"));
        assertTrue(writer.contains("CanonicalJsonSerializer"));
        assertTrue(writer.contains("application/json; charset=utf-8"));
        assertTrue(writer.contains("headers.set(\"Cache-Control\", \"no-store\")"));
        assertTrue(writer.contains("headers.set(\"X-Content-Type-Options\", \"nosniff\")"));
        assertTrue(writer.contains("static void applySecurityHeaders(Headers headers)"),
                "the security headers of the local server are declared once, for every writer of a local response");
        assertFalse(writer.contains("Strict-Transport-Security"),
                "the loopback server speaks plain HTTP and must not claim transport security");
        assertTrue(writer.contains("exchange.sendResponseHeaders(status, bytes.length)"));
        assertTrue(writer.contains("exchange.getResponseBody().write(bytes)"));
        assertFalse(writer.contains("MorpheusApiService"));
        assertFalse(writer.contains("MorpheusRemoteRoutePolicy"));
    }
}
