package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

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
            assertTrue(diagnostics.body().contains("\"requirementCoverageStatus\":\"MEASURED\""), diagnostics.body());
            assertTrue(diagnostics.body().contains("\"taskCoverageStatus\":"), diagnostics.body());
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
    void aSyncRefusedForInvalidContentIsABadRequestThatNamesTheFileRelativeToTheWorkspace() throws IOException {
        Path database = tempDirectory.resolve("invalid-content.db");
        Path workspace = Files.createDirectories(tempDirectory.resolve("untitled-openspec"));
        Files.createDirectories(workspace.resolve("openspec/specs/broken"));
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: spec-driven\n", StandardCharsets.UTF_8);
        Files.writeString(
                workspace.resolve("openspec/specs/broken/spec.md"),
                "## Requirements\n\n### Requirement: Untitled\nThe system SHALL reject an untitled specification.\n",
                StandardCharsets.UTF_8);

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response created = http.postJson(
                    server, "/projects", "{\"workspace\":" + http.jsonString(workspace.toString()) + "}");
            assertEquals(201, created.status(), created.body());
            String projectId = http.field(created.body(), "projectId");

            ApiTestSupport.Response refused = http.post(server, "/projects/" + projectId + "/sync");

            assertEquals(400, refused.status(), refused.body());
            assertTrue(refused.body().contains(
                    "openspec/specs/broken/spec.md: OpenSpec specification has no title"), refused.body());
            assertFalse(refused.body().contains(workspace.toString()), refused.body());
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

    /**
     * The sync answer names the requirement a delta file could not normalize, and the fence that was never closed,
     * through the remote-safe projection: this route is reachable by a remote WRITE caller. A title the location
     * predicate refuses ({@code Support TCP / UDP}) is therefore absent from this answer, while the CLI shows it
     * ({@code MorpheusCliTest}); the local projection wired here instead would relay it. Unlike
     * {@code MorpheusProjectSyncDisclosureTest}, nothing here depends on POSIX permissions, so it runs on every platform.
     * The keys of {@code data} are also held to the {@code required} list the contract publishes for {@code SyncResult},
     * which declares {@code additionalProperties: false}.
     */
    @Test
    void aSyncNamesTheRequirementItSkippedWithoutNamingTheServerWorkspace() throws IOException {
        Path database = tempDirectory.resolve("skipped-requirement.db");
        Path workspace = http.copyFixture("openspec-basic", tempDirectory.resolve("unclosed-openspec"));
        Path change = Files.createDirectories(workspace.resolve("openspec/changes/unclosed/specs/auth-session"))
                .getParent().getParent();
        Files.writeString(change.resolve("proposal.md"), """
                # Proposal: Unclosed example

                ## Intent

                Show that a requirement after a fence that is never closed is named.
                """, StandardCharsets.UTF_8);
        Files.writeString(change.resolve("specs/auth-session/spec.md"), """
                # Delta

                ## REMOVED Requirements

                ### Requirement: Legacy session warning

                ```inline``` markers are gone

                ## Notes

                ### Requirement: Keep the audit trail
                The system SHALL keep the audit trail.

                ### Requirement: Support TCP / UDP
                The system SHALL support both transports.
                """, StandardCharsets.UTF_8);

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response created = http.postJson(
                    server, "/projects", "{\"workspace\":" + http.jsonString(workspace.toString()) + "}");
            assertEquals(201, created.status(), created.body());
            String projectId = http.field(created.body(), "projectId");

            ApiTestSupport.Response sync = http.post(server, "/projects/" + projectId + "/sync");

            assertEquals(200, sync.status(), sync.body());
            String body = sync.body();
            JsonNode data = JsonMapper.builder().build().readTree(body).get("data");
            List<String> keys = new ArrayList<>();
            data.propertyNames().forEach(keys::add);
            assertEquals(new TreeSet<>(publishedRequired("SyncResult")), new TreeSet<>(keys), body);
            assertEquals(3, data.get("diagnostics").get("items").size(), body);
            assertTrue(body.contains("\"diagnosticCount\":3"), body);
            assertFalse(body.contains("TCP"), "the remote projection must drop a title that reads as a path: " + body);
            assertTrue(body.contains("\"code\":\"PARTIAL_INGESTION\""), body);
            assertTrue(body.contains("\"requirement\":\"Keep the audit trail\""), body);
            assertTrue(body.contains("\"code\":\"UNCLOSED_CODE_FENCE\""), body);
            assertTrue(body.contains("\"source\":\"openspec/changes/unclosed/specs/auth-session/spec.md\""), body);
            assertTrue(body.contains("\"truncated\":false,\"truncationReason\":null"), body);
            assertFalse(body.contains(workspace.toString()), body);
            assertFalse(body.contains(tempDirectory.toString()), body);
        }
    }

    private static List<String> publishedRequired(String schema) throws IOException {
        Path root = Path.of("").toAbsolutePath().normalize();
        while (root != null && !Files.exists(root.resolve("contracts/public-surfaces.tsv"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("repository root (contracts/public-surfaces.tsv) not found");
        }
        List<String> lines = Files.readAllLines(root.resolve("docs/openapi/morpheus-v1.yaml"), StandardCharsets.UTF_8);
        int start = lines.indexOf("    " + schema + ":");
        assertTrue(start >= 0, "docs/openapi/morpheus-v1.yaml has no schema " + schema);
        for (int index = start + 1; index < lines.size() && lines.get(index).startsWith("      "); index++) {
            String line = lines.get(index).trim();
            if (line.startsWith("required: [")) {
                return Arrays.stream(line.substring(line.indexOf('[') + 1, line.indexOf(']')).split(","))
                        .map(String::trim)
                        .toList();
            }
        }
        throw new AssertionError(schema + " publishes no required list");
    }
}
