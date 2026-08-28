package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusIntegrationStatusApiContractTest {
    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void minosAndNexusStatusRoutesRemainReadable() {
        Path database = tempDirectory.resolve("status.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response minos = http.get(server, "/integrations/minos/status");
            ApiTestSupport.Response nexus = http.get(server, "/integrations/nexus/status");

            assertEquals(200, minos.status(), minos.body());
            assertTrue(minos.body().contains("\"system\":\"MINOS\""), minos.body());
            assertEquals(200, nexus.status(), nexus.body());
            assertTrue(nexus.body().contains("\"system\":\"NEXUS\""), nexus.body());
        }
    }

    @Test
    void integrationStatusMethodAndQueryValidationRemainStableAfterExtraction() {
        Path database = tempDirectory.resolve("validation.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response wrongMethod = http.post(server, "/integrations/minos/status");
            ApiTestSupport.Response unknownQuery = http.get(server, "/integrations/minos/status?unexpected=true");

            assertEquals(405, wrongMethod.status(), wrongMethod.body());
            assertTrue(wrongMethod.body().contains("expected HTTP GET but received POST"), wrongMethod.body());
            assertEquals(400, unknownQuery.status(), unknownQuery.body());
            assertTrue(unknownQuery.body().contains("unknown query parameter: unexpected"), unknownQuery.body());
        }
    }

    @Test
    void unknownIntegrationKeepsSpecificNotFoundContract() {
        Path database = tempDirectory.resolve("unknown.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response response = http.get(server, "/integrations/other/status");

            assertEquals(404, response.status(), response.body());
            assertTrue(response.body().contains("unknown integration: other"), response.body());
        }
    }

    @Test
    void extraIntegrationStatusSegmentsKeepGenericNotFoundContract() {
        Path database = tempDirectory.resolve("extra.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response response = http.get(server, "/integrations/minos/status/extra");

            assertEquals(404, response.status(), response.body());
            assertTrue(response.body().contains(
                    "unknown API route: /api/v1/integrations/minos/status/extra"), response.body());
        }
    }
}
