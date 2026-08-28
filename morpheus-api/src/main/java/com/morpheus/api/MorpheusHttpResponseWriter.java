package com.morpheus.api;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * Owns the local HTTP facade's JSON serialization and response-header mechanics.
 *
 * <p>Status selection and API envelope construction remain responsibilities of
 * {@link MorpheusHttpServer}; this component only writes outcomes already decided by the facade.</p>
 */
final class MorpheusHttpResponseWriter {
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();

    void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = serializer.toUtf8(body);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", JSON_CONTENT_TYPE);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
