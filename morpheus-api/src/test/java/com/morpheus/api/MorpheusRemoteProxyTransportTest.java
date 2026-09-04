package com.morpheus.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRemoteProxyTransportTest {

    @Test
    void bodylessResponsesAreRecognizedWithoutReadingAnUpstreamBody() {
        assertTrue(MorpheusRemoteProxyTransport.isBodyless("HEAD", 200));
        assertTrue(MorpheusRemoteProxyTransport.isBodyless("head", 500));
        assertTrue(MorpheusRemoteProxyTransport.isBodyless("GET", 204));
        assertTrue(MorpheusRemoteProxyTransport.isBodyless("GET", 304));
        assertFalse(MorpheusRemoteProxyTransport.isBodyless("GET", 200));
    }

    @Test
    void declaredResponseLengthMustExistAndStayWithinTheConfiguredBound() {
        assertEquals(5L, MorpheusRemoteProxyTransport.requireBoundedLength(OptionalLong.of(5), 5));

        var missing = assertThrows(
                MorpheusRemoteProxyTransport.TransportException.class,
                () -> MorpheusRemoteProxyTransport.requireBoundedLength(OptionalLong.empty(), 5));
        assertEquals(502, missing.status());
        assertEquals("UPSTREAM_LENGTH_REQUIRED", missing.code());

        var negative = assertThrows(
                MorpheusRemoteProxyTransport.TransportException.class,
                () -> MorpheusRemoteProxyTransport.requireBoundedLength(OptionalLong.of(-1), 5));
        assertEquals(502, negative.status());
        assertEquals("UPSTREAM_LENGTH_REQUIRED", negative.code());

        var oversized = assertThrows(
                MorpheusRemoteProxyTransport.TransportException.class,
                () -> MorpheusRemoteProxyTransport.requireBoundedLength(OptionalLong.of(6), 5));
        assertEquals(502, oversized.status());
        assertEquals("UPSTREAM_RESPONSE_TOO_LARGE", oversized.code());
    }

    @Test
    void boundedCopyRequiresTheActualBodyToMatchBothDeclaredAndConfiguredLimits() throws Exception {
        byte[] payload = "morpheus".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteArrayOutputStream copied = new ByteArrayOutputStream();
        MorpheusRemoteProxyTransport.copyBounded(
                new ByteArrayInputStream(payload), copied, payload.length, payload.length);
        assertArrayEquals(payload, copied.toByteArray());

        assertThrows(IOException.class, () -> MorpheusRemoteProxyTransport.copyBounded(
                new ByteArrayInputStream(payload), new ByteArrayOutputStream(), payload.length - 1, payload.length));
        assertThrows(IOException.class, () -> MorpheusRemoteProxyTransport.copyBounded(
                new ByteArrayInputStream(payload, 0, payload.length - 1),
                new ByteArrayOutputStream(), payload.length, payload.length));
        assertThrows(IOException.class, () -> MorpheusRemoteProxyTransport.copyBounded(
                new ByteArrayInputStream(payload), new ByteArrayOutputStream(), payload.length, payload.length - 1));
    }

    @Test
    void saturatedResponseBudgetFailsClosedBeforeCallingTheLoopbackServer() {
        MorpheusRemoteRuntimeState runtime = new MorpheusRemoteRuntimeState(
                1,
                1,
                Duration.ofSeconds(1),
                16,
                16,
                1,
                Instant.EPOCH);
        MorpheusRemoteProxyTransport transport = new MorpheusRemoteProxyTransport(
                MorpheusInternalCapability.generate(),
                runtime,
                16,
                new Semaphore(0),
                HttpClient.newHttpClient());

        var failure = assertThrows(
                MorpheusRemoteProxyTransport.TransportException.class,
                () -> transport.forward(
                        new StubExchange("GET"),
                        URI.create("http://127.0.0.1:1/api/v1/health"),
                        new byte[0],
                        true));

        assertEquals(429, failure.status());
        assertEquals("RESPONSE_BUDGET_EXHAUSTED", failure.code());
        assertEquals(1L, runtime.statusAt("127.0.0.1", 8443, Instant.EPOCH).get("throttledRequests"));
    }

    @Test
    void responseBoundMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new MorpheusRemoteProxyTransport(
                MorpheusInternalCapability.generate(),
                new MorpheusRemoteRuntimeState(1, 1, Duration.ofSeconds(1), 1, 1, 1),
                0,
                new Semaphore(1),
                HttpClient.newHttpClient()));
    }

    private static final class StubExchange extends HttpExchange {
        private final String method;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int responseCode = -1;

        private StubExchange(String method) {
            this.method = method;
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
            return URI.create("/api/v1/health");
        }

        @Override
        public String getRequestMethod() {
            return method;
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
            return InputStream.nullInputStream();
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
            return new InetSocketAddress("127.0.0.1", 8443);
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
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
