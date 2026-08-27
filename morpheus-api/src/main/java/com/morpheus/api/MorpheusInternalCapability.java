package com.morpheus.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Per-process capability used only on the private loopback hop of the remote server. */
final class MorpheusInternalCapability {
    static final String HEADER = "X-Morpheus-Internal-Capability";
    private static final int TOKEN_BYTES = 32;
    private static final byte[] UNAUTHENTICATED_BODY = (
            "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"INTERNAL_AUTH_REQUIRED\","
                    + "\"message\":\"internal capability is required\",\"details\":{}}}")
            .getBytes(StandardCharsets.UTF_8);

    private final String token;
    private final byte[] tokenUtf8;

    private MorpheusInternalCapability(String token) {
        this.token = Objects.requireNonNull(token, "token");
        this.tokenUtf8 = token.getBytes(StandardCharsets.UTF_8);
    }

    static MorpheusInternalCapability generate() {
        byte[] random = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(random);
        try {
            return new MorpheusInternalCapability(Base64.getUrlEncoder().withoutPadding().encodeToString(random));
        } finally {
            java.util.Arrays.fill(random, (byte) 0);
        }
    }

    HttpHandler protect(HttpHandler downstream) {
        Objects.requireNonNull(downstream, "downstream");
        return exchange -> {
            if (!isAuthorized(exchange.getRequestHeaders())) {
                reject(exchange);
                return;
            }
            downstream.handle(exchange);
        };
    }

    void authorize(java.net.http.HttpRequest.Builder request) {
        Objects.requireNonNull(request, "request").header(HEADER, token);
    }

    boolean isAuthorized(Headers headers) {
        Objects.requireNonNull(headers, "headers");
        List<String> values = headers.get(HEADER);
        if (values == null || values.size() != 1) return false;
        String supplied = values.getFirst();
        if (supplied == null) return false;
        return MessageDigest.isEqual(tokenUtf8, supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static void reject(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        try {
            exchange.sendResponseHeaders(401, UNAUTHENTICATED_BODY.length);
            exchange.getResponseBody().write(UNAUTHENTICATED_BODY);
        } finally {
            exchange.close();
        }
    }
}
