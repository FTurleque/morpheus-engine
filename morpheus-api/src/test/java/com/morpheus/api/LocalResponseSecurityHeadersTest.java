package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every response of the local server carries the same security headers, whatever wrote it.
 *
 * <p>A refused origin was answered with {@code X-Frame-Options} and {@code Referrer-Policy}; a success, a 404 and an
 * application error of the same server were not, and none carried a content security policy. A refusal better
 * protected than a success is an asymmetry that ends up being read as a rule. The headers are read here on real
 * responses, one per way the server can answer, because that is what sees a path that forgot the shared writer.</p>
 */
class LocalResponseSecurityHeadersTest {
    private static final Map<String, String> EVERY_RESPONSE = Map.of(
            "Cache-Control", "no-store",
            "X-Content-Type-Options", "nosniff",
            "X-Frame-Options", "DENY",
            "Referrer-Policy", "no-referrer",
            "Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");

    @TempDir
    Path temporaryDirectory;

    @Test
    void aSuccessAnErrorAndARefusedOriginCarryTheSameSecurityHeaders() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("headers.db"), "127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            String base = server.baseUri().toString();
            Map<String, HttpResponse<String>> responses = new LinkedHashMap<>();
            responses.put("success", send(client, HttpRequest.newBuilder(URI.create(base + "/version")).GET()));
            responses.put("unknown route", send(client, HttpRequest.newBuilder(URI.create(base + "/nowhere")).GET()));
            responses.put("refused method", send(client, HttpRequest.newBuilder(URI.create(base + "/version"))
                    .method("DELETE", HttpRequest.BodyPublishers.noBody())));
            responses.put("refused body", send(client, HttpRequest.newBuilder(URI.create(base + "/reasoning/analyze"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"\",\"evidence\":[],\"adapterIds\":[]}"))));
            responses.put("refused origin", send(client, HttpRequest.newBuilder(URI.create(base + "/version"))
                    .header("Origin", "https://attacker.example").GET()));
            Files.write(temporaryDirectory.resolve("headers.db"),
                    "not a SQLite database, so that a store opened on it fails".repeat(64)
                            .getBytes(StandardCharsets.UTF_8));
            responses.put("state conflict", send(client, HttpRequest.newBuilder(URI.create(base + "/policies/evaluate"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"scopeKind\":\"PROJECT\","
                            + "\"scopeId\":\"11111111-1111-7111-8111-111111111111\"}"))));

            assertEquals(200, responses.get("success").statusCode());
            assertEquals(404, responses.get("unknown route").statusCode());
            assertEquals(405, responses.get("refused method").statusCode());
            assertEquals(400, responses.get("refused body").statusCode());
            assertEquals(403, responses.get("refused origin").statusCode());
            assertEquals(409, responses.get("state conflict").statusCode());

            responses.forEach((kind, response) -> {
                HttpHeaders headers = response.headers();
                EVERY_RESPONSE.forEach((name, value) -> assertEquals(value, headers.firstValue(name).orElse(null),
                        () -> "a " + kind + " response must carry " + name));
                assertFalse(headers.firstValue("Access-Control-Allow-Origin").isPresent(),
                        () -> "a " + kind + " response must carry no CORS header");
                assertFalse(headers.firstValue("Strict-Transport-Security").isPresent(),
                        () -> "the loopback server speaks plain HTTP: a " + kind + " response must not claim HTTPS");
            });
        }
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
