package com.morpheus.api;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

/**
 * Owns the remote facade's response envelope and security-header mechanics.
 *
 * <p>Status selection, authentication, authorization and proxy policy remain responsibilities of
 * {@link MorpheusRemoteHttpServer}; this component only renders outcomes already decided by the facade.</p>
 */
final class MorpheusRemoteResponseWriter {
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();

    void applySecurityHeaders(Headers headers, String requestId) {
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        headers.set("X-Request-Id", requestId);
    }

    void sendSuccess(HttpExchange exchange, int status, Object data) throws IOException {
        sendJson(exchange, status, success(data));
    }

    void sendError(HttpExchange exchange, int status, String code, String message) throws IOException {
        sendJson(exchange, status, error(code, message));
    }

    Map<String, Object> success(Object data) {
        return Map.of("apiVersion", "v1", "data", data);
    }

    Map<String, Object> error(String code, String message) {
        return Map.of(
                "apiVersion", "v1",
                "error", Map.of("code", code, "message", message, "details", Map.of()));
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", JSON_CONTENT_TYPE);
        byte[] body = serializer.toUtf8(payload);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
