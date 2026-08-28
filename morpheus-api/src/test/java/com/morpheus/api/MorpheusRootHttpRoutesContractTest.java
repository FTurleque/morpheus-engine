package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRootHttpRoutesContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesRootProductAndOperabilityContracts() {
        Path database = tempDirectory.resolve("root-routes.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            assertEquals(200, http.get(server, "").status());
            assertEquals(200, http.get(server, "/health").status());
            assertEquals(200, http.get(server, "/readiness").status());
            assertEquals(200, http.get(server, "/metrics").status());
            assertEquals(200, http.get(server, "/version").status());

            ApiTestSupport.Response method = http.post(server, "/health");
            assertEquals(405, method.status(), method.body());
            assertTrue(method.body().contains("expected HTTP GET but received POST"), method.body());
            assertEquals("GET", method.allow());

            ApiTestSupport.Response methodBeforeQuery = http.post(server, "/health?unexpected=true");
            assertEquals(405, methodBeforeQuery.status(), methodBeforeQuery.body());
            assertTrue(methodBeforeQuery.body().contains("expected HTTP GET but received POST"), methodBeforeQuery.body());

            ApiTestSupport.Response query = http.get(server, "/health?unexpected=true");
            assertEquals(400, query.status(), query.body());
            assertTrue(query.body().contains("unknown query parameter: unexpected"), query.body());

            ApiTestSupport.Response unknown = http.get(server, "/unknown");
            assertEquals(404, unknown.status(), unknown.body());
            assertTrue(unknown.body().contains("unknown API route"), unknown.body());
        }
    }
}
