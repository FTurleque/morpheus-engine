package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * The counters a started server serves are the ones its stores write to. Both sides are read through the server:
     * a store that counted somewhere the metrics route does not look would leave the served value where it was.
     */
    @Test
    void theServedMetricsCarryTheTransactionsOfTheStoresTheServerOpens() {
        Path database = tempDirectory.resolve("served-metrics.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            long before = servedCounter(server, "sqlite.transaction.committed");

            assertEquals(200, http.get(server, "/readiness").status());

            long after = servedCounter(server, "sqlite.transaction.committed");
            assertTrue(after > before, "the readiness probe opened a store and committed its transactions, and the "
                    + "served counter stayed at " + before);
        }
    }

    private long servedCounter(MorpheusHttpServer server, String name) {
        ApiTestSupport.Response metrics = http.get(server, "/metrics");
        assertEquals(200, metrics.status(), metrics.body());
        Matcher served = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(\\d+)").matcher(metrics.body());
        assertTrue(served.find(), () -> "the metrics route does not serve " + name + ": " + metrics.body());
        return Long.parseLong(served.group(1));
    }
}
