package com.morpheus.api;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusPortfolioApiContractTest {
    @TempDir
    Path temporaryDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void portfolioRegistryPersistsProjectScopeAndMissingIsNonDestructive() {
        Path database = temporaryDirectory.resolve("morpheus.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response created = http.postJson(server, "/portfolios", "{\"name\":\"Platform\"}");
            assertEquals(201, created.status(), created.body());
            String portfolioId = firstUuid(created.body());
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();

            ApiTestSupport.Response registered = http.postJson(
                    server,
                    "/portfolios/" + portfolioId + "/projects",
                    "{\"projectId\":\"" + projectId + "\",\"name\":\"Alpha\","
                            + "\"workspace\":\"/work/alpha\","
                            + "\"repository\":\"git:https://example.test/alpha.git\","
                            + "\"providers\":\"openspec\"}");
            ApiTestSupport.Response missing = http.post(
                    server, "/portfolios/" + portfolioId + "/projects/" + projectId + "/missing");
            ApiTestSupport.Response overview = http.get(server, "/portfolios/" + portfolioId);

            assertEquals(201, registered.status(), registered.body());
            assertEquals(200, missing.status(), missing.body());
            assertEquals(200, overview.status(), overview.body());
            assertTrue(overview.body().contains(projectId.toString()), overview.body());
            assertTrue(overview.body().contains("\"status\":\"MISSING\""), overview.body());
        }
    }

    @Test
    void traversalBudgetsAreValidatedByTheSharedApplicationService() {
        Path database = temporaryDirectory.resolve("morpheus.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response created = http.postJson(server, "/portfolios", "{\"name\":\"Platform\"}");
            String portfolioId = firstUuid(created.body());
            ApiTestSupport.Response response = http.postJson(
                    server,
                    "/portfolios/" + portfolioId + "/traverse",
                    "{\"startProjectId\":\"" + ProjectSpecificationId.generate() + "\","
                            + "\"startType\":\"requirement\","
                            + "\"startId\":\"01900000-0000-7000-8000-000000000001\","
                            + "\"maxDepth\":9}");

            assertEquals(400, response.status(), response.body());
            assertTrue(response.body().contains("maxDepth must be between 1 and 8"), response.body());
        }
    }

    private String firstUuid(String json) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("UUIDv7 not found in " + json);
        }
        return matcher.group();
    }
}
