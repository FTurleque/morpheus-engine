package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusProjectSyncHttpRoutesContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesSyncAndSyncStatusMethodPathQueryBodyAndRangeContracts() {
        Path database = tempDirectory.resolve("project-sync-routes.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response syncExtraSegment = http.get(
                    server, "/projects/project-1/sync/extra?unexpected=true");
            assertEquals(404, syncExtraSegment.status(), syncExtraSegment.body());
            assertTrue(syncExtraSegment.body().contains("unknown API route"), syncExtraSegment.body());

            ApiTestSupport.Response syncMethod = http.get(server, "/projects/project-1/sync?unexpected=true");
            assertEquals(405, syncMethod.status(), syncMethod.body());
            assertEquals("POST", syncMethod.allow());
            assertTrue(syncMethod.body().contains("expected HTTP POST but received GET"), syncMethod.body());

            ApiTestSupport.Response syncQuery = http.post(server, "/projects/project-1/sync?unexpected=true");
            assertEquals(400, syncQuery.status(), syncQuery.body());
            assertTrue(syncQuery.body().contains("unknown query parameter: unexpected"), syncQuery.body());

            ApiTestSupport.Response syncContentType = http.postWithoutContentType(
                    server, "/projects/project-1/sync", "{\"revision\":\"revision-1\"}");
            assertEquals(415, syncContentType.status(), syncContentType.body());
            assertTrue(syncContentType.body().contains("UNSUPPORTED_MEDIA_TYPE"), syncContentType.body());

            ApiTestSupport.Response statusExtraSegment = http.request(
                    server, "/projects/project-1/sync-status/extra?unexpected=true", "POST");
            assertEquals(404, statusExtraSegment.status(), statusExtraSegment.body());
            assertTrue(statusExtraSegment.body().contains("unknown API route"), statusExtraSegment.body());

            ApiTestSupport.Response statusMethod = http.post(
                    server, "/projects/project-1/sync-status?unexpected=true");
            assertEquals(405, statusMethod.status(), statusMethod.body());
            assertEquals("GET", statusMethod.allow());
            assertTrue(statusMethod.body().contains("expected HTTP GET but received POST"), statusMethod.body());

            ApiTestSupport.Response statusQuery = http.get(
                    server, "/projects/project-1/sync-status?unexpected=true");
            assertEquals(400, statusQuery.status(), statusQuery.body());
            assertTrue(statusQuery.body().contains("unknown query parameter: unexpected"), statusQuery.body());

            ApiTestSupport.Response tooSmall = http.get(
                    server, "/projects/project-1/sync-status?maxAgeMinutes=0");
            assertEquals(400, tooSmall.status(), tooSmall.body());
            assertTrue(tooSmall.body().contains("maxAgeMinutes must be between 1 and "), tooSmall.body());

            ApiTestSupport.Response tooLarge = http.get(
                    server,
                    "/projects/project-1/sync-status?maxAgeMinutes="
                            + (MorpheusApiService.MAX_MAX_AGE_MINUTES + 1));
            assertEquals(400, tooLarge.status(), tooLarge.body());
            assertTrue(tooLarge.body().contains("maxAgeMinutes must be between 1 and "), tooLarge.body());

            String registrationBody = "{\"workspace\":"
                    + http.jsonString(http.fixture("openspec-basic").toString()) + "}";
            ApiTestSupport.Response created = http.postJson(server, "/projects", registrationBody);
            assertEquals(201, created.status(), created.body());
            String projectId = http.field(created.body(), "projectId");

            ApiTestSupport.Response syncWithoutBody = http.post(server, "/projects/" + projectId + "/sync");
            assertEquals(200, syncWithoutBody.status(), syncWithoutBody.body());
            assertTrue(syncWithoutBody.body().contains("\"published\":true"), syncWithoutBody.body());

            ApiTestSupport.Response defaultStatus = http.get(server, "/projects/" + projectId + "/sync-status");
            assertEquals(200, defaultStatus.status(), defaultStatus.body());
            assertTrue(defaultStatus.body().contains("\"state\":\"FRESH\""), defaultStatus.body());

            ApiTestSupport.Response maximumStatus = http.get(
                    server,
                    "/projects/" + projectId + "/sync-status?maxAgeMinutes="
                            + MorpheusApiService.MAX_MAX_AGE_MINUTES);
            assertEquals(200, maximumStatus.status(), maximumStatus.body());
        }
    }
}
