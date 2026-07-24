package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusApiContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void exposesVersionedHealthProjectsAndStableErrorEnvelopes() {
        Path database = tempDirectory.resolve("morpheus.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response root = http.get(server, "/");
            assertEquals(200, root.status());
            assertTrue(root.contentType().startsWith("application/json"));
            assertTrue(root.body().contains("\"apiVersion\":\"v1\""));

            ApiTestSupport.Response health = http.get(server, "/health");
            assertEquals(200, health.status());
            assertTrue(health.body().contains("\"status\":\"UP\""));

            ApiTestSupport.Response version = http.get(server, "/version");
            assertEquals(200, version.status());
            assertTrue(version.body().contains("0.1.0-SNAPSHOT"));

            ApiTestSupport.Response projects = http.get(server, "/projects");
            assertEquals(200, projects.status());
            assertTrue(projects.body().contains("\"data\":[]"));

            ApiTestSupport.Response missing = http.get(server, "/does-not-exist");
            assertEquals(404, missing.status());
            assertTrue(missing.body().contains("\"code\":\"NOT_FOUND\""));

            ApiTestSupport.Response method = http.post(server, "/health");
            assertEquals(405, method.status());
            assertTrue(method.body().contains("METHOD_NOT_ALLOWED"));

            ApiTestSupport.Response unsupported = http.postWithoutContentType(
                    server, "/projects", "{\"workspace\":\"x\"}");
            assertEquals(415, unsupported.status());
            assertTrue(unsupported.body().contains("UNSUPPORTED_MEDIA_TYPE"));

            ApiTestSupport.Response unknownField = http.postJson(
                    server, "/projects", "{\"workspace\":\"x\",\"unexpected\":true}");
            assertEquals(400, unknownField.status());
            assertTrue(unknownField.body().contains("BAD_REQUEST"));

            ApiTestSupport.Response badQuery = http.get(server, "/projects?unexpected=true");
            assertEquals(400, badQuery.status());
            assertTrue(badQuery.body().contains("unknown query parameter"));
        }
    }
}
