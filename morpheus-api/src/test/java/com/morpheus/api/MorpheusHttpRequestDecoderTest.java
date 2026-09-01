package com.morpheus.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusHttpRequestDecoderTest {

    @Test
    void requiredJsonAcceptsApplicationJsonAndDecodesStrictly() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            MorpheusHttpRequestDecoder decoder = new MorpheusHttpRequestDecoder(256, Duration.ofSeconds(1), executor);
            StubExchange exchange = jsonExchange("{\"name\":\"morpheus\"}");

            Payload decoded = decoder.readRequiredJson(exchange, Payload.class);

            assertEquals("morpheus", decoded.name());
        }
    }

    @Test
    void requiredJsonRejectsEmptyBodyBeforeContentTypeValidation() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            MorpheusHttpRequestDecoder decoder = new MorpheusHttpRequestDecoder(256, Duration.ofSeconds(1), executor);
            StubExchange exchange = new StubExchange(new byte[0]);

            ApiFailure failure = assertThrows(
                    ApiFailure.class,
                    () -> decoder.readRequiredJson(exchange, Payload.class));

            assertEquals(400, failure.status());
            assertEquals("BAD_REQUEST", failure.code());
            assertEquals("JSON request body is required", failure.getMessage());
        }
    }

    @Test
    void optionalJsonReturnsDefaultForEmptyBodyWithoutRequiringContentType() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            MorpheusHttpRequestDecoder decoder = new MorpheusHttpRequestDecoder(256, Duration.ofSeconds(1), executor);
            Payload defaultValue = new Payload("default");

            Payload decoded = decoder.readOptionalJson(new StubExchange(new byte[0]), Payload.class, defaultValue);

            assertSame(defaultValue, decoded);
        }
    }

    @Test
    void nonEmptyJsonRequiresApplicationJsonContentType() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            MorpheusHttpRequestDecoder decoder = new MorpheusHttpRequestDecoder(256, Duration.ofSeconds(1), executor);
            StubExchange exchange = new StubExchange("{\"name\":\"morpheus\"}".getBytes(StandardCharsets.UTF_8));
            exchange.getRequestHeaders().set("Content-Type", "text/plain");

            ApiFailure failure = assertThrows(
                    ApiFailure.class,
                    () -> decoder.readRequiredJson(exchange, Payload.class));

            assertEquals(415, failure.status());
            assertEquals("UNSUPPORTED_MEDIA_TYPE", failure.code());
        }
    }

    @Test
    void strictMapperRejectsUnknownPropertiesAndTrailingTokens() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            MorpheusHttpRequestDecoder decoder = new MorpheusHttpRequestDecoder(256, Duration.ofSeconds(1), executor);

            ApiFailure unknown = assertThrows(
                    ApiFailure.class,
                    () -> decoder.readRequiredJson(jsonExchange("{\"name\":\"morpheus\",\"extra\":true}"), Payload.class));
            ApiFailure trailing = assertThrows(
                    ApiFailure.class,
                    () -> decoder.readRequiredJson(jsonExchange("{\"name\":\"morpheus\"} {}"), Payload.class));

            assertEquals(400, unknown.status());
            assertEquals("BAD_REQUEST", unknown.code());
            assertTrue(unknown.getMessage().startsWith("invalid JSON request body:"));
            assertEquals(400, trailing.status());
            assertTrue(trailing.getMessage().startsWith("invalid JSON request body:"));
        }
    }

    @Test
    void bodyLimitFailsClosedWithTheConfiguredBound() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            MorpheusHttpRequestDecoder decoder = new MorpheusHttpRequestDecoder(8, Duration.ofSeconds(1), executor);
            StubExchange exchange = jsonExchange("{\"name\":\"too-large\"}");

            ApiFailure failure = assertThrows(
                    ApiFailure.class,
                    () -> decoder.readRequiredJson(exchange, Payload.class));

            assertEquals(400, failure.status());
            assertEquals("BAD_REQUEST", failure.code());
            assertEquals("request body exceeds 8 bytes", failure.getMessage());
        }
    }

    @Test
    void constructorRejectsNonPositiveBounds() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Duration oneSecond = Duration.ofSeconds(1);
            assertThrows(IllegalArgumentException.class,
                    () -> new MorpheusHttpRequestDecoder(0, oneSecond, executor));
            assertThrows(IllegalArgumentException.class,
                    () -> new MorpheusHttpRequestDecoder(1, Duration.ZERO, executor));
            assertThrows(IllegalArgumentException.class,
                    () -> new MorpheusHttpRequestDecoder(1, Duration.ofSeconds(-1), executor));
        }
    }

    private static StubExchange jsonExchange(String body) {
        StubExchange exchange = new StubExchange(body.getBytes(StandardCharsets.UTF_8));
        exchange.getRequestHeaders().set("Content-Type", "application/json; charset=utf-8");
        return exchange;
    }

    private record Payload(String name) {
    }

    private static final class StubExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final byte[] requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int responseCode = -1;

        private StubExchange(byte[] requestBody) {
            this.requestBody = requestBody;
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return URI.create("/api/v1/test");
        }

        @Override
        public String getRequestMethod() {
            return "POST";
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return new ByteArrayInputStream(requestBody);
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            responseCode = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8765);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream input, OutputStream output) {
            // no-op: this stub serves fixed in-memory streams and never needs the
            // server's filtered/wrapped input and output streams substituted in
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
