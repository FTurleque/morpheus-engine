package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusAugmentedContextApiContractTest {
    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void disabledNexusStillReturnsDeterministicRequirementAndChangeIntentWithoutMutatingActiveSnapshot() {
        Path database = tempDirectory.resolve("m13-api.db");
        Path fixture = http.fixture("openspec-basic");

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response nexusStatus = http.get(server, "/integrations/nexus/status");
            assertEquals(200, nexusStatus.status(), nexusStatus.body());
            assertTrue(nexusStatus.body().contains("\"system\":\"NEXUS\""), nexusStatus.body());
            assertTrue(nexusStatus.body().contains("\"state\":\"DISABLED\""), nexusStatus.body());

            String registrationBody = "{\"workspace\":" + http.jsonString(fixture.toString()) + "}";
            ApiTestSupport.Response created = http.postJson(server, "/projects", registrationBody);
            String projectId = http.field(created.body(), "projectId");
            ApiTestSupport.Response sync = http.post(server, "/projects/" + projectId + "/sync");
            assertEquals(200, sync.status(), sync.body());
            String activeSnapshotId = http.field(sync.body(), "snapshotId");

            ApiTestSupport.Response requirements = http.get(
                    server, "/projects/" + projectId + "/requirements?query=session&limit=50");
            String requirementId = http.field(requirements.body(), "id");
            ApiTestSupport.Response requirementContext = http.postJson(
                    server,
                    "/projects/" + projectId + "/requirements/" + requirementId + "/augmented-context",
                    "{\"nexusProject\":\"morpheus-engine\",\"tokenBudget\":1234,"
                            + "\"requestedSources\":[\"FILE\",\"SYMBOL\"],"
                            + "\"constraints\":{\"language\":\"java\"},\"explain\":true}");
            assertEquals(200, requirementContext.status(), requirementContext.body());
            assertTrue(requirementContext.body().contains("\"subjectType\":\"REQUIREMENT\""), requirementContext.body());
            assertTrue(requirementContext.body().contains("Requirement:"), requirementContext.body());
            assertTrue(requirementContext.body().contains("Statement:"), requirementContext.body());
            assertTrue(requirementContext.body().contains("\"state\":\"DISABLED\""), requirementContext.body());
            assertTrue(requirementContext.body().contains("\"persisted\":false"), requirementContext.body());

            ApiTestSupport.Response changes = http.get(server, "/projects/" + projectId + "/changes");
            String changeId = http.field(changes.body(), "id");
            ApiTestSupport.Response changeContext = http.postJson(
                    server,
                    "/projects/" + projectId + "/changes/" + changeId + "/augmented-context",
                    "{\"nexusProject\":\"morpheus-engine\"}");
            assertEquals(200, changeContext.status(), changeContext.body());
            assertTrue(changeContext.body().contains("\"subjectType\":\"CHANGE\""), changeContext.body());
            assertTrue(changeContext.body().contains("Change:"), changeContext.body());
            assertTrue(changeContext.body().contains("Intent:"), changeContext.body());
            assertTrue(changeContext.body().contains("\"persisted\":false"), changeContext.body());

            ApiTestSupport.Response project = http.get(server, "/projects/" + projectId);
            assertEquals(200, project.status(), project.body());
            assertTrue(project.body().contains(activeSnapshotId), project.body());
        }
    }
}
