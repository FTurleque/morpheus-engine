package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusSpecificationsHttpRoutesContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void rejectsUnsupportedMethodsAndUnknownQueriesBeforeServiceAccess() {
        Path database = tempDirectory.resolve("specifications-routes.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response method = http.post(server, "/projects/project-1/specifications");
            assertEquals(405, method.status(), method.body());
            assertTrue(method.body().contains("expected HTTP GET but received POST"), method.body());

            ApiTestSupport.Response listQuery = http.get(
                    server, "/projects/project-1/specifications?unexpected=true");
            assertEquals(400, listQuery.status(), listQuery.body());
            assertTrue(listQuery.body().contains("unknown query parameter: unexpected"), listQuery.body());

            ApiTestSupport.Response detailQuery = http.get(
                    server, "/projects/project-1/specifications/specification-1?unexpected=true");
            assertEquals(400, detailQuery.status(), detailQuery.body());
            assertTrue(detailQuery.body().contains("unknown query parameter: unexpected"), detailQuery.body());
        }
    }

    @Test
    void preservesPaginationValidationAndUnknownRouteErrors() {
        Path database = tempDirectory.resolve("specifications-pagination.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response invalidLimit = http.get(
                    server, "/projects/project-1/specifications/specification-1/context?limit=0");
            assertEquals(400, invalidLimit.status(), invalidLimit.body());
            assertTrue(invalidLimit.body().contains("limit must be between 1 and 100"), invalidLimit.body());

            ApiTestSupport.Response unknownChild = http.get(
                    server, "/projects/project-1/specifications/specification-1/other");
            assertEquals(404, unknownChild.status(), unknownChild.body());
            assertTrue(unknownChild.body().contains("unknown specifications route"), unknownChild.body());

            ApiTestSupport.Response extraSegment = http.get(
                    server, "/projects/project-1/specifications/specification-1/context/extra");
            assertEquals(404, extraSegment.status(), extraSegment.body());
            assertTrue(extraSegment.body().contains("unknown specifications route"), extraSegment.body());
        }
    }
}
