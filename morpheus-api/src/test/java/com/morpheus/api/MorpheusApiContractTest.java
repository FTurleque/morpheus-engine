package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
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

            ApiTestSupport.Response readiness = http.get(server, "/readiness");
            assertEquals(200, readiness.status(), readiness.body());
            assertTrue(readiness.body().contains("\"status\":\"READY\""), readiness.body());

            ApiTestSupport.Response metrics = http.get(server, "/metrics");
            assertEquals(200, metrics.status(), metrics.body());
            assertTrue(metrics.body().contains("\"counters\""), metrics.body());
            assertTrue(metrics.body().contains("\"timings\""), metrics.body());

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

            String registration = "{\"workspace\":" + http.jsonString(http.fixture("openspec-basic").toString()) + "}";
            ApiTestSupport.Response created = http.postJson(server, "/projects", registration);
            assertEquals(201, created.status(), created.body());
            String projectId = http.field(created.body(), "projectId");

            ApiTestSupport.Response unpublished = http.get(server, "/projects/" + projectId + "/requirements");
            assertEquals(409, unpublished.status(), unpublished.body());
            assertTrue(unpublished.body().contains("\"code\":\"STATE_CONFLICT\""), unpublished.body());

            ApiTestSupport.Response unknownProject = http.get(
                    server, "/projects/01900000-0000-7000-8000-000000000001/requirements");
            assertEquals(404, unknownProject.status(), unknownProject.body());
            assertTrue(unknownProject.body().contains("\"code\":\"NOT_FOUND\""), unknownProject.body());
        }
    }

    @Test
    void reportsNotReadyWithServiceUnavailableWhenTheLocalDatabaseCannotBeOpened() throws Exception {
        Path database = tempDirectory.resolve("not-ready.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            Files.delete(database);
            Files.createDirectory(database);

            ApiTestSupport.Response health = http.get(server, "/health");
            assertEquals(200, health.status(), health.body());
            assertTrue(health.body().contains("\"status\":\"UP\""), health.body());

            ApiTestSupport.Response readiness = http.get(server, "/readiness");
            assertEquals(503, readiness.status(), readiness.body());
            assertTrue(readiness.body().contains("\"status\":\"NOT_READY\""), readiness.body());
            assertTrue(readiness.body().contains("\"diagnosticCode\":\"DATABASE_NOT_READY\""), readiness.body());
        }
    }
}
