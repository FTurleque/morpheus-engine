package com.morpheus.api.local;

import com.morpheus.api.transport.MorpheusHttpResponseWriter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Delegating server that applies the loopback Host/Origin policy to every registered local HTTP context. */
final class LoopbackRequestProtectedHttpServer extends RequestProtectedHttpServer {
    private static final byte[] REJECTED_BODY = ("{\"apiVersion\":\"v1\",\"error\":{"
            + "\"code\":\"LOCAL_REQUEST_ORIGIN_REJECTED\","
            + "\"message\":\"local MORPHEUS API accepts loopback hosts and origins only\","
            + "\"details\":{}}}").getBytes(StandardCharsets.UTF_8);

    LoopbackRequestProtectedHttpServer(HttpServer delegate) {
        super(delegate, LoopbackRequestProtectedHttpServer::protect);
    }

    private static HttpHandler protect(HttpHandler handler) {
        return exchange -> {
            try {
                LoopbackRequestPolicy.requireAllowed(exchange);
            } catch (LoopbackRequestPolicy.RejectedRequestException rejected) {
                reject(exchange);
                return;
            }
            handler.handle(exchange);
        };
    }

    private static void reject(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        MorpheusHttpResponseWriter.applySecurityHeaders(headers);
        exchange.sendResponseHeaders(403, REJECTED_BODY.length);
        exchange.getResponseBody().write(REJECTED_BODY);
        exchange.close();
    }
}
