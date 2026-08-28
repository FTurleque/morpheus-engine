package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusProjectRootHttpRoutesContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesProjectCollectionAndDetailMethodQueryBodyAndStatusContracts() {
        Path database = tempDirectory.resolve("project-root-routes.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response collectionQueryBeforeMethod = http.request(
                    server, "/projects?unexpected=true", "PUT");
            assertEquals(400, collectionQueryBeforeMethod.status(), collectionQueryBeforeMethod.body());
            assertTrue(
                    collectionQueryBeforeMethod.body().contains("unknown query parameter: unexpected"),
                    collectionQueryBeforeMethod.body());

            ApiTestSupport.Response collectionMethod = http.request(server, "/projects", "PUT");
            assertEquals(405, collectionMethod.status(), collectionMethod.body());
            assertEquals("GET, POST", collectionMethod.allow());
            assertTrue(collectionMethod.body().contains("projects supports GET and POST"), collectionMethod.body());

            ApiTestSupport.Response missingContentType = http.postWithoutContentType(
                    server, "/projects", "{\"workspace\":\"ignored\"}");
            assertEquals(415, missingContentType.status(), missingContentType.body());
            assertTrue(missingContentType.body().contains("UNSUPPORTED_MEDIA_TYPE"), missingContentType.body());

            ApiTestSupport.Response blankWorkspace = http.postJson(server, "/projects", "{\"workspace\":\"   \"}");
            assertEquals(400, blankWorkspace.status(), blankWorkspace.body());
            assertTrue(blankWorkspace.body().contains("workspace is required"), blankWorkspace.body());

            ApiTestSupport.Response detailMethodBeforeQuery = http.request(
                    server, "/projects/project-1?unexpected=true", "POST");
            assertEquals(405, detailMethodBeforeQuery.status(), detailMethodBeforeQuery.body());
            assertEquals("GET", detailMethodBeforeQuery.allow());
            assertTrue(
                    detailMethodBeforeQuery.body().contains("expected HTTP GET but received POST"),
                    detailMethodBeforeQuery.body());

            ApiTestSupport.Response detailQuery = http.get(server, "/projects/project-1?unexpected=true");
            assertEquals(400, detailQuery.status(), detailQuery.body());
            assertTrue(detailQuery.body().contains("unknown query parameter: unexpected"), detailQuery.body());

            String registrationBody = "{\"workspace\":"
                    + http.jsonString(http.fixture("openspec-basic").toString()) + "}";
            ApiTestSupport.Response created = http.postJson(server, "/projects", registrationBody);
            assertEquals(201, created.status(), created.body());
            String projectId = http.field(created.body(), "projectId");

            ApiTestSupport.Response repeated = http.postJson(server, "/projects", registrationBody);
            assertEquals(200, repeated.status(), repeated.body());
            assertEquals(projectId, http.field(repeated.body(), "projectId"));

            ApiTestSupport.Response listed = http.get(server, "/projects");
            assertEquals(200, listed.status(), listed.body());
            assertTrue(listed.body().contains(projectId), listed.body());

            ApiTestSupport.Response detail = http.get(server, "/projects/" + projectId);
            assertEquals(200, detail.status(), detail.body());
            assertTrue(detail.body().contains(projectId), detail.body());
        }
    }
}
