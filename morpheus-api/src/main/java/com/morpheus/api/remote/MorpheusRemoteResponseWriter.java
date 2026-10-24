package com.morpheus.api.remote;

import com.morpheus.api.transport.TimedBoundedResponseWriter;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;

/**
 * Owns the remote facade's response envelope and security-header mechanics.
 *
 * <p>Status selection, authentication, authorization and proxy policy remain responsibilities of
 * {@link MorpheusRemoteHttpServer}; this component only renders outcomes already decided by the facade.</p>
 *
 * <p>Even a small envelope is written under the facade's response deadline. An envelope is a few hundred bytes
 * and normally disappears into the socket buffer, but "normally" is a property of the client, and a client that
 * never reads is exactly the one this bound exists for.</p>
 */
final class MorpheusRemoteResponseWriter {
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();
    private final TimedBoundedResponseWriter bounded;

    MorpheusRemoteResponseWriter(TimedBoundedResponseWriter bounded) {
        this.bounded = Objects.requireNonNull(bounded, "bounded");
    }

    void applySecurityHeaders(Headers headers, String requestId) {
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        // The facade serves HTTPS only. One year, for the host the operator exposes and no other: MORPHEUS does not own
        // that domain, so it does not speak for its subdomains.
        headers.set("Strict-Transport-Security", "max-age=31536000");
        headers.set("X-Request-Id", requestId);
    }

    void sendSuccess(HttpExchange exchange, int status, Object data) throws IOException {
        sendJson(exchange, status, success(data));
    }

    /**
     * Renders a refusal, and gives up the connection when the request carried a body.
     *
     * <p>Most refusals are decided before the body is read: an unknown token, a role that is not enough, a ceiling
     * reached. Once such a response is sent the server finds the body unread and may drop the connection, while the
     * client, which has its answer, sends its next request on it and waits for a reply that never comes. Saying
     * {@code Connection: close} makes both sides agree: the server closes after this exchange and the client opens
     * another connection. A refusal of a request without a body keeps its connection.</p>
     */
    void sendError(HttpExchange exchange, int status, String code, String message) throws IOException {
        if (declaresABody(exchange.getRequestHeaders())) {
            exchange.getResponseHeaders().set("Connection", "close");
        }
        sendJson(exchange, status, error(code, message));
    }

    static boolean declaresABody(Headers requestHeaders) {
        String length = requestHeaders.getFirst("Content-Length");
        return requestHeaders.containsKey("Transfer-Encoding") || (length != null && !length.trim().equals("0"));
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
        bounded.write(progress -> {
            exchange.sendResponseHeaders(status, body.length);
            OutputStream downstream = exchange.getResponseBody();
            downstream.write(body);
            progress.made();
            downstream.flush();
        });
    }
}
