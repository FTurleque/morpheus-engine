package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusChangesHttpRoutesContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesMethodQueryBodyDepthAndPathContracts() {
        Path database = tempDirectory.resolve("changes-routes.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response listMethod = http.post(server, "/projects/project-1/changes");
            assertEquals(405, listMethod.status(), listMethod.body());
            assertTrue(listMethod.body().contains("expected HTTP GET but received POST"), listMethod.body());

            ApiTestSupport.Response augmentedMethod = http.get(
                    server, "/projects/project-1/changes/change-1/augmented-context");
            assertEquals(405, augmentedMethod.status(), augmentedMethod.body());
            assertTrue(augmentedMethod.body().contains("expected HTTP POST but received GET"), augmentedMethod.body());

            ApiTestSupport.Response transitionMethod = http.get(
                    server, "/projects/project-1/changes/change-1/transition-check");
            assertEquals(405, transitionMethod.status(), transitionMethod.body());
            assertTrue(transitionMethod.body().contains("expected HTTP POST but received GET"), transitionMethod.body());

            ApiTestSupport.Response lifecycleMethod = http.get(
                    server, "/projects/project-1/changes/change-1/lifecycle-transitions");
            assertEquals(405, lifecycleMethod.status(), lifecycleMethod.body());
            assertTrue(lifecycleMethod.body().contains("expected HTTP POST but received GET"), lifecycleMethod.body());

            ApiTestSupport.Response missingBody = http.post(
                    server, "/projects/project-1/changes/change-1/augmented-context");
            assertEquals(400, missingBody.status(), missingBody.body());
            assertTrue(missingBody.body().contains("JSON request body is required"), missingBody.body());

            ApiTestSupport.Response unknownQuery = http.get(
                    server, "/projects/project-1/changes?unexpected=true");
            assertEquals(400, unknownQuery.status(), unknownQuery.body());
            assertTrue(unknownQuery.body().contains("unknown query parameter: unexpected"), unknownQuery.body());

            ApiTestSupport.Response invalidDepth = http.get(
                    server, "/projects/project-1/changes/change-1/context?depth=0");
            assertEquals(400, invalidDepth.status(), invalidDepth.body());
            assertTrue(invalidDepth.body().contains("depth must be between 1 and 20"), invalidDepth.body());

            ApiTestSupport.Response unknown = http.get(
                    server, "/projects/project-1/changes/change-1/unknown");
            assertEquals(404, unknown.status(), unknown.body());
            assertTrue(unknown.body().contains("unknown change subresource: unknown"), unknown.body());
        }
    }

    @Test
    void acceptanceCriteriaUsesTheSharedPaginationContract() {
        Path database = tempDirectory.resolve("acceptance-pagination.db");
        Path fixture = http.fixture("openspec-basic");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            String registration = "{\"workspace\":" + http.jsonString(fixture.toString()) + "}";
            ApiTestSupport.Response created = http.postJson(server, "/projects", registration);
            assertEquals(201, created.status(), created.body());
            String projectId = http.field(created.body(), "projectId");

            ApiTestSupport.Response sync = http.postJson(
                    server, "/projects/" + projectId + "/sync", "{\"revision\":\"pagination-r1\"}");
            assertEquals(200, sync.status(), sync.body());

            ApiTestSupport.Response changes = http.get(server, "/projects/" + projectId + "/changes?limit=1");
            assertEquals(200, changes.status(), changes.body());
            String changeId = http.field(changes.body(), "id");

            ApiTestSupport.Response page = http.get(
                    server,
                    "/projects/" + projectId + "/changes/" + changeId
                            + "/acceptance-criteria?offset=1&limit=1");
            assertEquals(200, page.status(), page.body());
            assertTrue(page.body().contains("\"offset\":1"), page.body());
            assertTrue(page.body().contains("\"limit\":1"), page.body());

            ApiTestSupport.Response invalidLimit = http.get(
                    server,
                    "/projects/" + projectId + "/changes/" + changeId
                            + "/acceptance-criteria?limit=101");
            assertEquals(400, invalidLimit.status(), invalidLimit.body());
            assertTrue(invalidLimit.body().contains("limit must be between 1 and 100"), invalidLimit.body());
        }
    }
}
