package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusDiagnosticsHttpRoutesContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesMethodQueryAndExactPathGuards() {
        Path database = tempDirectory.resolve("diagnostics-routes.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response method = http.post(server, "/projects/project-1/diagnostics");
            assertEquals(405, method.status(), method.body());
            assertTrue(method.body().contains("expected HTTP GET but received POST"), method.body());

            ApiTestSupport.Response query = http.get(
                    server, "/projects/project-1/diagnostics?unexpected=true");
            assertEquals(400, query.status(), query.body());
            assertTrue(query.body().contains("unknown query parameter: unexpected"), query.body());

            ApiTestSupport.Response extraSegment = http.get(
                    server, "/projects/project-1/diagnostics/extra");
            assertEquals(404, extraSegment.status(), extraSegment.body());
            assertTrue(extraSegment.body().contains("unknown API route"), extraSegment.body());
        }
    }
}
