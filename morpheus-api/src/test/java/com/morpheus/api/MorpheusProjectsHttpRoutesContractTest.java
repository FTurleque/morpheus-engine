package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusProjectsHttpRoutesContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesCollectionDetailSyncAndDispatchContracts() {
        Path database = tempDirectory.resolve("projects-routes.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response unknownQuery = http.get(server, "/projects?unexpected=true");
            assertEquals(400, unknownQuery.status(), unknownQuery.body());
            assertTrue(unknownQuery.body().contains("unknown query parameter: unexpected"), unknownQuery.body());

            ApiTestSupport.Response missingRegistrationBody = http.post(server, "/projects");
            assertEquals(400, missingRegistrationBody.status(), missingRegistrationBody.body());
            assertTrue(missingRegistrationBody.body().contains("JSON request body is required"), missingRegistrationBody.body());

            ApiTestSupport.Response detailMethod = http.post(server, "/projects/project-1");
            assertEquals(405, detailMethod.status(), detailMethod.body());
            assertTrue(detailMethod.body().contains("expected HTTP GET but received POST"), detailMethod.body());

            ApiTestSupport.Response syncMethod = http.get(server, "/projects/project-1/sync");
            assertEquals(405, syncMethod.status(), syncMethod.body());
            assertTrue(syncMethod.body().contains("expected HTTP POST but received GET"), syncMethod.body());

            ApiTestSupport.Response syncQuery = http.post(server, "/projects/project-1/sync?unexpected=true");
            assertEquals(400, syncQuery.status(), syncQuery.body());
            assertTrue(syncQuery.body().contains("unknown query parameter: unexpected"), syncQuery.body());

            ApiTestSupport.Response invalidMaxAge = http.get(
                    server, "/projects/project-1/sync-status?maxAgeMinutes=0");
            assertEquals(400, invalidMaxAge.status(), invalidMaxAge.body());
            assertTrue(invalidMaxAge.body().contains("maxAgeMinutes must be between 1 and 525600"), invalidMaxAge.body());

            ApiTestSupport.Response extraSyncSegment = http.get(server, "/projects/project-1/sync-status/extra");
            assertEquals(404, extraSyncSegment.status(), extraSyncSegment.body());
            assertTrue(extraSyncSegment.body().contains("unknown API route"), extraSyncSegment.body());

            ApiTestSupport.Response unknownResource = http.get(server, "/projects/project-1/unknown");
            assertEquals(404, unknownResource.status(), unknownResource.body());
            assertTrue(unknownResource.body().contains("unknown project API resource: unknown"), unknownResource.body());
        }
    }
}
