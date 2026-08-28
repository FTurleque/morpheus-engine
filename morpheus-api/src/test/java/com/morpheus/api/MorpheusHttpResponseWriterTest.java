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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MorpheusHttpResponseWriterTest {

    @Test
    void sendWritesCanonicalJsonStatusAndExactLength() throws Exception {
        MorpheusHttpResponseWriter writer = new MorpheusHttpResponseWriter();
        StubExchange exchange = new StubExchange();

        writer.send(exchange, 201, Map.of("z", 2, "a", "morpheus"));

        byte[] body = exchange.responseBody.toByteArray();
        assertEquals(201, exchange.responseCode);
        assertEquals(body.length, exchange.responseLength);
        assertEquals("{\"a\":\"morpheus\",\"z\":2}", new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void sendKeepsLocalJsonSecurityHeadersAndRemainsCorsFree() throws Exception {
        MorpheusHttpResponseWriter writer = new MorpheusHttpResponseWriter();
        StubExchange exchange = new StubExchange();

        writer.send(exchange, 200, Map.of("state", "READY"));

        Headers headers = exchange.getResponseHeaders();
        assertEquals("application/json; charset=utf-8", headers.getFirst("Content-Type"));
        assertEquals("no-store", headers.getFirst("Cache-Control"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertFalse(headers.containsKey("Access-Control-Allow-Origin"));
    }

    @Test
    void sendPreservesVersionedServerEnvelopeShape() throws Exception {
        MorpheusHttpResponseWriter writer = new MorpheusHttpResponseWriter();
        StubExchange exchange = new StubExchange();
        MorpheusHttpServer.ApiSuccess envelope = new MorpheusHttpServer.ApiSuccess(
                "v1", Map.of("service", "morpheus"));

        writer.send(exchange, 200, envelope);

        assertEquals(
                "{\"apiVersion\":\"v1\",\"data\":{\"service\":\"morpheus\"}}",
                exchange.responseBody.toString(StandardCharsets.UTF_8));
    }

    private static final class StubExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int responseCode = -1;
        private long responseLength = -1;

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
            return "GET";
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
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            this.responseCode = rCode;
            this.responseLength = responseLength;
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
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
