package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusCompositionHttpRoutesContractTest {
    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void methodAndQueryValidationRemainStableAfterExtraction() {
        Path database = tempDirectory.resolve("validation.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response wrongMethod = http.post(server, "/projects/project-1/composition");
            ApiTestSupport.Response unknownQuery = http.get(
                    server,
                    "/projects/project-1/composition?unexpected=true");

            assertEquals(405, wrongMethod.status(), wrongMethod.body());
            assertTrue(wrongMethod.body().contains("expected HTTP GET but received POST"), wrongMethod.body());
            assertEquals(400, unknownQuery.status(), unknownQuery.body());
            assertTrue(unknownQuery.body().contains("unknown query parameter: unexpected"), unknownQuery.body());
        }
    }

    @Test
    void unknownCompositionChildrenKeepSpecificNotFoundContract() {
        Path database = tempDirectory.resolve("routing.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response unknown = http.get(server, "/projects/project-1/composition/other");
            ApiTestSupport.Response extra = http.get(server, "/projects/project-1/composition/conflicts/extra");

            assertEquals(404, unknown.status(), unknown.body());
            assertTrue(unknown.body().contains("unknown composition route"), unknown.body());
            assertEquals(404, extra.status(), extra.body());
            assertTrue(extra.body().contains("unknown composition route"), extra.body());
        }
    }
}
