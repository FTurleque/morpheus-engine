package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusVersionsHttpRoutesContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesMethodQueryPaginationAndPathContracts() {
        Path database = tempDirectory.resolve("versions-routes.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response method = http.post(server, "/projects/project-1/versions");
            assertEquals(405, method.status(), method.body());
            assertTrue(method.body().contains("expected HTTP GET but received POST"), method.body());

            ApiTestSupport.Response unknownQuery = http.get(server, "/projects/project-1/versions?unexpected=true");
            assertEquals(400, unknownQuery.status(), unknownQuery.body());
            assertTrue(unknownQuery.body().contains("unknown query parameter: unexpected"), unknownQuery.body());

            ApiTestSupport.Response missingFrom = http.get(
                    server, "/projects/project-1/versions/compare?toSnapshotId=to-1");
            assertEquals(400, missingFrom.status(), missingFrom.body());
            assertTrue(missingFrom.body().contains("query parameter is required: fromSnapshotId"), missingFrom.body());

            ApiTestSupport.Response invalidLimit = http.get(
                    server, "/projects/project-1/versions/snapshot-1/requirements?limit=0");
            assertEquals(400, invalidLimit.status(), invalidLimit.body());
            assertTrue(invalidLimit.body().contains("limit must be between 1 and 100"), invalidLimit.body());

            ApiTestSupport.Response unknown = http.get(server, "/projects/project-1/versions/unknown");
            assertEquals(404, unknown.status(), unknown.body());
            assertTrue(unknown.body().contains("unknown versions route"), unknown.body());
        }
    }
}
