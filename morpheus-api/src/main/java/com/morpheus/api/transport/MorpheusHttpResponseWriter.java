package com.morpheus.api.transport;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * Owns the local HTTP facade's JSON serialization and response-header mechanics, for the server and for the
 * routers that register their own HTTP context.
 *
 * <p>Status selection and API envelope construction remain responsibilities of the caller; this component only
 * writes outcomes already decided.</p>
 *
 * <p>{@link #sendRaw} is the general case and {@link #send} its JSON specialization. The split is not a
 * generalization made on speculation: {@code MorpheusQueryHttpRoutes} already wrote exactly this pair, because a
 * bounded export answers with its own media type and bytes the serializer never produces. Every response still
 * carries {@code Cache-Control} and {@code X-Content-Type-Options}, export included -- that is what made the export
 * a missing parameter here rather than a response of a different kind.</p>
 */
public final class MorpheusHttpResponseWriter {
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();

    public void send(HttpExchange exchange, int status, Object body) throws IOException {
        sendRaw(exchange, status, JSON_CONTENT_TYPE, serializer.toUtf8(body));
    }

    public void sendRaw(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        applySecurityHeaders(headers);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    /**
     * The security headers of every response of the local server, declared once: a success, an error and a refused
     * origin are protected alike. No transport-security header: this server speaks plain HTTP on loopback, where the
     * header would be ignored and would state something false.
     */
    public static void applySecurityHeaders(Headers headers) {
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    }
}
