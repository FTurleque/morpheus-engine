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
    void extractedPortfolioReadRoutesPreservePaginationAndEmptyViews() {
        Path database = temporaryDirectory.resolve("reads.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response created = http.postJson(server, "/portfolios", "{\"name\":\"Platform\"}");
            String portfolioId = firstUuid(created.body());
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            ApiTestSupport.Response registered = http.postJson(
                    server,
                    "/portfolios/" + portfolioId + "/projects",
                    "{\"projectId\":\"" + projectId + "\",\"name\":\"Alpha\"}");

            ApiTestSupport.Response portfolios = http.get(server, "/portfolios?offset=0&limit=10");
            ApiTestSupport.Response members = http.get(
                    server, "/portfolios/" + portfolioId + "/members?offset=0&limit=10");
            ApiTestSupport.Response references = http.get(
                    server, "/portfolios/" + portfolioId + "/references?projectId=%20&offset=0&limit=10");
            ApiTestSupport.Response conflicts = http.get(server, "/portfolios/" + portfolioId + "/conflicts");

            assertEquals(201, registered.status(), registered.body());
            assertEquals(200, portfolios.status(), portfolios.body());
            assertTrue(portfolios.body().contains(portfolioId), portfolios.body());
            assertEquals(200, members.status(), members.body());
            assertTrue(members.body().contains(projectId.toString()), members.body());
            assertEquals(200, references.status(), references.body());
            assertEquals(200, conflicts.status(), conflicts.body());
        }
    }

    @Test
    void extractedPortfolioMutationAndTraversalRoutesPreserveContracts() {
        Path database = temporaryDirectory.resolve("mutations.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            String portfolioId = firstUuid(http.postJson(
                    server, "/portfolios", "{\"name\":\"Platform\"}").body());
            ProjectSpecificationId sourceProject = ProjectSpecificationId.generate();
            ProjectSpecificationId targetProject = ProjectSpecificationId.generate();
            register(server, portfolioId, sourceProject, "Source");
            register(server, portfolioId, targetProject, "Target");

            ApiTestSupport.Response freshness = http.postJson(
                    server,
                    "/portfolios/" + portfolioId + "/projects/" + sourceProject + "/freshness",
                    "{\"state\":\"FRESH\",\"revision\":\"rev-42\",\"explanation\":\"incremental\"}");

            String sourceEntity = "01900000-0000-7000-8000-000000000001";
            String targetEntity = "01900000-0000-7000-8000-000000000002";
            ApiTestSupport.Response reference = http.postJson(
                    server,
                    "/portfolios/" + portfolioId + "/references",
                    "{\"sourceProjectId\":\"" + sourceProject + "\","
                            + "\"sourceType\":\"requirement\",\"sourceId\":\"" + sourceEntity + "\","
                            + "\"targetProjectId\":\"" + targetProject + "\","
                            + "\"targetType\":\"specification\",\"targetId\":\"" + targetEntity + "\","
                            + "\"relation\":\"DEPENDS_ON\",\"providerId\":\"reference\"}");
            ApiTestSupport.Response references = http.get(
                    server, "/portfolios/" + portfolioId + "/references?projectId=" + sourceProject);
            ApiTestSupport.Response traversal = http.postJson(
                    server,
                    "/portfolios/" + portfolioId + "/traverse",
                    "{\"startProjectId\":\"" + sourceProject + "\","
                            + "\"startType\":\"requirement\",\"startId\":\"" + sourceEntity + "\","
                            + "\"direction\":\"OUTGOING\",\"maxDepth\":2,\"maxNodes\":10,\"maxLinks\":10}");

            assertEquals(200, freshness.status(), freshness.body());
            assertEquals(201, reference.status(), reference.body());
            assertEquals(200, references.status(), references.body());
            assertTrue(references.body().contains("DEPENDS_ON"), references.body());
            assertEquals(200, traversal.status(), traversal.body());
        }
    }

    @Test
    void extractedPortfolioRouteErrorsRemainStable() {
        Path database = temporaryDirectory.resolve("errors.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            String portfolioId = firstUuid(http.postJson(
                    server, "/portfolios", "{\"name\":\"Platform\"}").body());

            ApiTestSupport.Response rootMethod = http.request(server, "/portfolios", "DELETE");
            ApiTestSupport.Response referencesMethod = http.request(
                    server, "/portfolios/" + portfolioId + "/references", "PUT");
            ApiTestSupport.Response projectRoute = http.get(
                    server, "/portfolios/" + portfolioId + "/projects/project/unknown");
            ApiTestSupport.Response unknownResource = http.get(
                    server, "/portfolios/" + portfolioId + "/unknown");
            ApiTestSupport.Response extraMembersSegment = http.get(
                    server, "/portfolios/" + portfolioId + "/members/extra");

            assertEquals(405, rootMethod.status(), rootMethod.body());
            assertTrue(rootMethod.body().contains("portfolios supports GET and POST"), rootMethod.body());
            assertEquals(405, referencesMethod.status(), referencesMethod.body());
            assertTrue(referencesMethod.body().contains("portfolio references supports GET and POST"), referencesMethod.body());
            assertEquals(404, projectRoute.status(), projectRoute.body());
            assertTrue(projectRoute.body().contains("unknown portfolio projects route"), projectRoute.body());
            assertEquals(404, unknownResource.status(), unknownResource.body());
            assertTrue(unknownResource.body().contains("unknown portfolio API resource"), unknownResource.body());
            assertEquals(404, extraMembersSegment.status(), extraMembersSegment.body());
            assertTrue(extraMembersSegment.body().contains("unknown API route"), extraMembersSegment.body());
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

    private void register(
            MorpheusHttpServer server,
            String portfolioId,
            ProjectSpecificationId projectId,
            String name) {
        ApiTestSupport.Response response = http.postJson(
                server,
                "/portfolios/" + portfolioId + "/projects",
                "{\"projectId\":\"" + projectId + "\",\"name\":\"" + name + "\"}");
        assertEquals(201, response.status(), response.body());
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
