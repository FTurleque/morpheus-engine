package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusExternalReferenceHttpRoutesContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesMethodQueryAndPathContracts() {
        Path database = tempDirectory.resolve("external-reference-routes.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response method = http.post(server, "/projects/project-1/external-references");
            assertEquals(405, method.status(), method.body());
            assertTrue(method.body().contains("expected HTTP GET but received POST"), method.body());

            ApiTestSupport.Response requiredOwner = http.get(server, "/projects/project-1/external-references");
            assertEquals(400, requiredOwner.status(), requiredOwner.body());
            assertTrue(requiredOwner.body().contains("query parameter is required: ownerId"), requiredOwner.body());

            ApiTestSupport.Response resolutionQuery = http.get(
                    server, "/projects/project-1/external-references/reference-1/resolution?unexpected=true");
            assertEquals(400, resolutionQuery.status(), resolutionQuery.body());
            assertTrue(resolutionQuery.body().contains("unknown query parameter: unexpected"), resolutionQuery.body());

            ApiTestSupport.Response unknown = http.get(
                    server, "/projects/project-1/external-references/reference-1/unknown");
            assertEquals(404, unknown.status(), unknown.body());
            assertTrue(unknown.body().contains("unknown external-references route"), unknown.body());
        }
    }
}
