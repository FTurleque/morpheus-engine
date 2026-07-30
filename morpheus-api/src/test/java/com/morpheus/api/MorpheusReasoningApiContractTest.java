package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusReasoningApiContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void listsAdaptersWithoutActivatingThem() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("adapters.db"), "127.0.0.1", 0)) {
            HttpResponse<String> response = get(URI.create(server.baseUri() + "/reasoning/adapters"));

            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("builtin-evidence-synthesis-v1"), response.body());
            assertFalse(response.body().contains("executions"), response.body());
        }
    }

    @Test
    void factsOnlyRequestReturnsNoAssistedClaimsAndNoMutation() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("facts.db"), "127.0.0.1", 0)) {
            HttpResponse<String> response = postJson(
                    URI.create(server.baseUri() + "/reasoning/analyze"),
                    """
                    {"question":"What is authoritative?","evidence":[
                      {"id":"fact-1","kind":"PUBLISHED_FACT","subject":"history",
                       "statement":"Published history is authoritative","provenance":{"source":"snapshot"}}
                    ],"adapterIds":[]}
                    """);

            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("\"assisted\":false"), response.body());
            assertTrue(response.body().contains("\"mutated\":false"), response.body());
            assertTrue(response.body().contains("\"facts\""), response.body());
        }
    }

    @Test
    void explicitAdapterProducesEvidenceBackedSeparatedClaims() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("assisted.db"), "127.0.0.1", 0)) {
            HttpResponse<String> response = postJson(
                    URI.create(server.baseUri() + "/reasoning/analyze"),
                    """
                    {"question":"Can remote mode be enabled safely?","evidence":[
                      {"id":"fact-1","kind":"PUBLISHED_FACT","subject":"remote","statement":"TLS is required"},
                      {"id":"fact-2","kind":"PUBLISHED_FACT","subject":"remote","statement":"Authentication is required"}
                    ],"adapterIds":["builtin-evidence-synthesis-v1"],"maxClaims":10}
                    """);

            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("\"assisted\":true"), response.body());
            assertTrue(response.body().contains("\"inferences\""), response.body());
            assertTrue(response.body().contains("\"heuristics\""), response.body());
            assertTrue(response.body().contains("\"evidenceIds\""), response.body());
            assertTrue(response.body().contains("\"mutated\":false"), response.body());
        }
    }

    @Test
    void rejectsUnknownPropertiesAndUnknownAdaptersExplicitly() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("invalid.db"), "127.0.0.1", 0)) {
            URI uri = URI.create(server.baseUri() + "/reasoning/analyze");
            HttpResponse<String> unknownProperty = postJson(uri,
                    "{\"question\":\"Q\",\"script\":\"return true\"}");
            HttpResponse<String> unknownAdapter = postJson(uri,
                    "{\"question\":\"Q\",\"adapterIds\":[\"missing-adapter\"]}");

            assertEquals(400, unknownProperty.statusCode(), unknownProperty.body());
            assertTrue(unknownProperty.body().contains("BAD_REQUEST"), unknownProperty.body());
            assertEquals(400, unknownAdapter.statusCode(), unknownAdapter.body());
            assertTrue(unknownAdapter.body().contains("REASONING_VALIDATION"), unknownAdapter.body());
        }
    }

    private HttpResponse<String> postJson(URI uri, String body) throws Exception {
        return send(HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return send(HttpRequest.newBuilder(uri).GET().build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
