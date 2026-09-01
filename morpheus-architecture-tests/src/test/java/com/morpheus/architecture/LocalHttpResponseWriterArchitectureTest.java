package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpResponseWriterArchitectureTest {

    @Test
    void localHttpResponseWritingStaysExtractedCanonicalAndHardened() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String writer = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpResponseWriter.java"));

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
        assertTrue(writer.contains("exchange.sendResponseHeaders(status, bytes.length)"));
        assertTrue(writer.contains("exchange.getResponseBody().write(bytes)"));
        assertFalse(writer.contains("MorpheusApiService"));
        assertFalse(writer.contains("MorpheusRemoteRoutePolicy"));
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate MORPHEUS repository root");
    }
}
