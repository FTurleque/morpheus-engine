package com.morpheus.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityProtectedHttpServerTest {

    @Test
    void protectsRootAndMoreSpecificContextsIncludingLateHandlerAssignment() throws Exception {
        HttpServer delegate = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        MorpheusInternalCapability capability = MorpheusInternalCapability.generate();
        HttpServer server = new CapabilityProtectedHttpServer(delegate, capability);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext(MorpheusHttpRouteTable.API_PREFIX, CapabilityProtectedHttpServerTest::noContent);
        var childContext = server.createContext(MorpheusHttpRouteTable.API_PREFIX + "/reasoning");
        childContext.setHandler(CapabilityProtectedHttpServerTest::noContent);
        UnsupportedOperationException filterFailure =
                assertThrows(UnsupportedOperationException.class, () -> childContext.getFilters().clear());
        assertEquals("clear", filterFailure.getStackTrace()[0].getMethodName());
        assertTrue(filterFailure.getStackTrace()[0].getClassName().startsWith("java.util.Collections$Unmodifiable"),
                "the filter list of a protected context is an unmodifiable view");
        server.start();

        try {
            URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + MorpheusHttpRouteTable.API_PREFIX);
            URI child = URI.create(root + "/reasoning/analyze");
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<Void> refused = client.send(
                    HttpRequest.newBuilder(root).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(401, refused.statusCode());
            assertEquals("no-store", refused.headers().firstValue("Cache-Control").orElse(null));
            assertEquals("nosniff", refused.headers().firstValue("X-Content-Type-Options").orElse(null));
            assertEquals("DENY", refused.headers().firstValue("X-Frame-Options").orElse(null));
            assertEquals("no-referrer", refused.headers().firstValue("Referrer-Policy").orElse(null));
            assertEquals("default-src 'none'; frame-ancestors 'none'",
                    refused.headers().firstValue("Content-Security-Policy").orElse(null),
                    "a request refused for want of the internal capability is a local response like any other");
            assertEquals(401, client.send(
                    HttpRequest.newBuilder(child)
                            .header(MorpheusInternalCapability.HEADER, "attacker-controlled")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());

            HttpRequest.Builder authorized = HttpRequest.newBuilder(child).GET();
            capability.authorize(authorized);
            assertEquals(204, client.send(
                    authorized.build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static void noContent(HttpExchange exchange) throws IOException {
        try {
            exchange.sendResponseHeaders(204, -1);
        } finally {
            exchange.close();
        }
    }
}
