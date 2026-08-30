package com.morpheus.api;

import com.sun.net.httpserver.Authenticator;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopbackRequestProtectedHttpServerTest {

    @Test
    void delegatesLifecycleAndProtectsDirectAndLateBoundContexts() throws Exception {
        HttpServer delegate = HttpServer.create();
        HttpServer server = new LoopbackRequestProtectedHttpServer(delegate);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        assertSame(executor, server.getExecutor());
        assertEquals(delegate.getAddress(), server.getAddress());

        var direct = server.createContext("/direct", LoopbackRequestProtectedHttpServerTest::noContent);
        assertEquals("/direct", direct.getPath());
        assertSame(server, direct.getServer());
        direct.getAttributes().put("marker", "value");
        assertEquals("value", direct.getAttributes().get("marker"));
        assertTrue(direct.getFilters().isEmpty());
        assertTrue(direct.getHandler() != null);

        Authenticator authenticator = new Authenticator() {
            @Override
            public Result authenticate(HttpExchange exchange) {
                return new Failure(401);
            }
        };
        assertNull(direct.setAuthenticator(authenticator));
        assertSame(authenticator, direct.getAuthenticator());
        assertSame(authenticator, direct.setAuthenticator(null));
        assertNull(direct.getAuthenticator());

        var late = server.createContext("/late");
        late.setHandler(LoopbackRequestProtectedHttpServerTest::noContent);
        assertThrows(NullPointerException.class, () -> late.setHandler(null));

        var wrappedRemoval = server.createContext("/wrapped-removal", LoopbackRequestProtectedHttpServerTest::noContent);
        server.removeContext(wrappedRemoval);
        var rawRemoval = delegate.createContext("/raw-removal", LoopbackRequestProtectedHttpServerTest::noContent);
        server.removeContext(rawRemoval);
        server.createContext("/path-removal", LoopbackRequestProtectedHttpServerTest::noContent);
        server.removeContext("/path-removal");

        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> allowed = client.send(
                    HttpRequest.newBuilder(base.resolve("/direct")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(204, allowed.statusCode());

            HttpResponse<String> lateBound = client.send(
                    HttpRequest.newBuilder(base.resolve("/late")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(204, lateBound.statusCode());

            HttpResponse<String> rejected = client.send(
                    HttpRequest.newBuilder(base.resolve("/direct"))
                            .header("Origin", "https://attacker.example")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, rejected.statusCode());
            assertEquals("application/json; charset=utf-8", rejected.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("no-store", rejected.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals("nosniff", rejected.headers().firstValue("X-Content-Type-Options").orElseThrow());
            assertEquals("DENY", rejected.headers().firstValue("X-Frame-Options").orElseThrow());
            assertEquals("no-referrer", rejected.headers().firstValue("Referrer-Policy").orElseThrow());
            assertTrue(rejected.body().contains("LOCAL_REQUEST_ORIGIN_REJECTED"));
            assertFalse(rejected.body().isBlank());
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsNullDelegateAndNullDirectHandler() throws Exception {
        assertThrows(NullPointerException.class, () -> new LoopbackRequestProtectedHttpServer(null));

        HttpServer delegate = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        HttpServer server = new LoopbackRequestProtectedHttpServer(delegate);
        try {
            assertThrows(NullPointerException.class, () -> server.createContext("/null", null));
        } finally {
            server.stop(0);
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
