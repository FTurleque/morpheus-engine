package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusApiProjectSyncIntegrationTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void registersSyncsQueriesAndReopensEntireHeadlessSurface() {
        Path database = tempDirectory.resolve("morpheus.db");
        Path fixture = http.fixture("openspec-basic");
        String projectId;
        String requirementId;

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            String registrationBody = "{\"workspace\":" + http.jsonString(fixture.toString()) + "}";
            ApiTestSupport.Response created = http.postJson(server, "/projects", registrationBody);
            assertEquals(201, created.status(), created.body());
            projectId = http.field(created.body(), "projectId");

            ApiTestSupport.Response idempotent = http.postJson(server, "/projects", registrationBody);
            assertEquals(200, idempotent.status(), idempotent.body());
            assertTrue(idempotent.body().contains(projectId));

            ApiTestSupport.Response project = http.get(server, "/projects/" + projectId);
            assertEquals(200, project.status(), project.body());
            assertTrue(project.body().contains("\"activeSnapshotId\":\"none\""));

            ApiTestSupport.Response sync = http.post(server, "/projects/" + projectId + "/sync");
            assertEquals(200, sync.status(), sync.body());
            assertTrue(sync.body().contains("\"mode\":\"FULL_REBUILD\""), sync.body());
            assertTrue(sync.body().contains("\"published\":true"), sync.body());
            assertTrue(sync.body().contains("\"requirementCount\":2"), sync.body());

            ApiTestSupport.Response status = http.get(server, "/projects/" + projectId + "/sync-status");
            assertEquals(200, status.status(), status.body());
            assertTrue(status.body().contains("\"state\":\"FRESH\""), status.body());
            assertTrue(status.body().contains("\"lastSuccessfulMode\":\"FULL_REBUILD\""), status.body());

            ApiTestSupport.Response specifications = http.get(server, "/projects/" + projectId + "/specifications");
            assertEquals(200, specifications.status(), specifications.body());
            String specificationId = http.field(specifications.body(), "id");

            ApiTestSupport.Response specification = http.get(
                    server, "/projects/" + projectId + "/specifications/" + specificationId);
            assertEquals(200, specification.status(), specification.body());

            ApiTestSupport.Response specificationContext = http.get(
                    server, "/projects/" + projectId + "/specifications/" + specificationId + "/context");
            assertEquals(200, specificationContext.status(), specificationContext.body());
            assertTrue(specificationContext.body().contains("scenarios"), specificationContext.body());

            ApiTestSupport.Response requirements = http.get(
                    server, "/projects/" + projectId + "/requirements?query=session&limit=50");
            assertEquals(200, requirements.status(), requirements.body());
            assertTrue(requirements.body().contains("session-expiration"), requirements.body());
            requirementId = http.field(requirements.body(), "id");

            ApiTestSupport.Response requirement = http.get(
                    server, "/projects/" + projectId + "/requirements/" + requirementId);
            assertEquals(200, requirement.status(), requirement.body());

            ApiTestSupport.Response trace = http.get(
                    server, "/projects/" + projectId + "/requirements/" + requirementId + "/trace?depth=2");
            assertEquals(200, trace.status(), trace.body());
            assertTrue(trace.body().contains(requirementId), trace.body());

            ApiTestSupport.Response changes = http.get(server, "/projects/" + projectId + "/changes");
            assertEquals(200, changes.status(), changes.body());
            String changeId = http.field(changes.body(), "id");

            assertEquals(200, http.get(server, "/projects/" + projectId + "/changes/" + changeId).status());
            assertEquals(200, http.get(server, "/projects/" + projectId + "/changes/" + changeId + "/constraints").status());
            assertEquals(200, http.get(server, "/projects/" + projectId + "/changes/" + changeId + "/design-decisions").status());
            assertEquals(200, http.get(server, "/projects/" + projectId + "/changes/" + changeId + "/implementation-tasks").status());

            ApiTestSupport.Response acceptance = http.get(
                    server, "/projects/" + projectId + "/changes/" + changeId + "/acceptance-criteria");
            assertEquals(200, acceptance.status(), acceptance.body());
            assertTrue(acceptance.body().contains("\"totalMatches\":0"), acceptance.body());
            assertTrue(acceptance.body().contains("\"items\":[]"), acceptance.body());
            assertTrue(acceptance.body().contains("\"hasMore\":false"), acceptance.body());
            assertFalse(acceptance.body().contains("UNAVAILABLE_IN_NORMALIZED_MODEL"), acceptance.body());

            ApiTestSupport.Response context = http.get(
                    server, "/projects/" + projectId + "/changes/" + changeId + "/context?depth=2");
            assertEquals(200, context.status(), context.body());

            ApiTestSupport.Response lifecycle = http.get(
                    server, "/projects/" + projectId + "/changes/" + changeId + "/status");
            assertEquals(200, lifecycle.status(), lifecycle.body());
            assertTrue(lifecycle.body().contains("UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT"), lifecycle.body());

            ApiTestSupport.Response blockers = http.get(
                    server, "/projects/" + projectId + "/changes/" + changeId + "/blocking-conditions");
            assertEquals(200, blockers.status(), blockers.body());
            assertTrue(blockers.body().contains("unavailableFacts"), blockers.body());

            ApiTestSupport.Response diagnostics = http.get(server, "/projects/" + projectId + "/diagnostics");
            assertEquals(200, diagnostics.status(), diagnostics.body());
            assertTrue(diagnostics.body().contains("get_quality_report"), diagnostics.body());
        }

        try (MorpheusHttpServer reopened = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response projects = http.get(reopened, "/projects");
            assertEquals(200, projects.status(), projects.body());
            assertTrue(projects.body().contains(projectId), projects.body());

            ApiTestSupport.Response requirement = http.get(
                    reopened, "/projects/" + projectId + "/requirements/" + requirementId);
            assertEquals(200, requirement.status(), requirement.body());
            assertTrue(requirement.body().contains(requirementId), requirement.body());
        }
    }

    @Test
    void failedSyncNeverReplacesPreviouslyPublishedActiveSnapshot() throws IOException {
        Path database = tempDirectory.resolve("failure-preservation.db");
        Path workspace = http.copyFixture("openspec-basic", tempDirectory.resolve("mutable-openspec"));

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            String registrationBody = "{\"workspace\":" + http.jsonString(workspace.toString()) + "}";
            ApiTestSupport.Response created = http.postJson(server, "/projects", registrationBody);
            assertEquals(201, created.status(), created.body());
            String projectId = http.field(created.body(), "projectId");

            ApiTestSupport.Response firstSync = http.postJson(
                    server, "/projects/" + projectId + "/sync", "{\"revision\":\"good\"}");
            assertEquals(200, firstSync.status(), firstSync.body());
            String activeSnapshotId = http.field(firstSync.body(), "snapshotId");

            Path specificationFile;
            try (var files = Files.walk(workspace.resolve("openspec/specs"))) {
                specificationFile = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("mutable fixture contains no specification markdown"));
            }
            Files.writeString(
                    specificationFile,
                    "# Broken specification\n\n### Requirement: Missing statement\n",
                    StandardCharsets.UTF_8);

            ApiTestSupport.Response failed = http.postJson(
                    server, "/projects/" + projectId + "/sync", "{\"revision\":\"broken\"}");
            assertTrue(failed.status() >= 400, failed.body());

            ApiTestSupport.Response projectAfterFailure = http.get(server, "/projects/" + projectId);
            assertEquals(200, projectAfterFailure.status(), projectAfterFailure.body());
            assertTrue(projectAfterFailure.body().contains(activeSnapshotId), projectAfterFailure.body());

            ApiTestSupport.Response requirements = http.get(server, "/projects/" + projectId + "/requirements");
            assertEquals(200, requirements.status(), requirements.body());
            assertTrue(requirements.body().contains("\"totalMatches\":2"), requirements.body());

            ApiTestSupport.Response versions = http.get(server, "/projects/" + projectId + "/versions");
            assertEquals(200, versions.status(), versions.body());
            assertTrue(versions.body().contains(activeSnapshotId), versions.body());
            assertTrue(!versions.body().contains("\"snapshotState\":\"RETIRED\""), versions.body());
        }
    }
}
