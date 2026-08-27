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

class CapabilityProtectedHttpServerTest {

    @Test
    void protectsRootAndMoreSpecificContextsIncludingLateHandlerAssignment() throws Exception {
        HttpServer delegate = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        MorpheusInternalCapability capability = MorpheusInternalCapability.generate();
        HttpServer server = new CapabilityProtectedHttpServer(delegate, capability);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext(MorpheusHttpServer.API_PREFIX, CapabilityProtectedHttpServerTest::noContent);
        server.createContext(MorpheusHttpServer.API_PREFIX + "/reasoning")
                .setHandler(CapabilityProtectedHttpServerTest::noContent);
        server.start();

        try {
            URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + MorpheusHttpServer.API_PREFIX);
            URI child = URI.create(root + "/reasoning/analyze");
            HttpClient client = HttpClient.newHttpClient();

            assertEquals(401, client.send(
                    HttpRequest.newBuilder(root).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
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
