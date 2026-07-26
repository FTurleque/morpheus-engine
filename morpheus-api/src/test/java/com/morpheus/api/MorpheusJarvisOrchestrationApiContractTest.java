package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusJarvisOrchestrationApiContractTest {
    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void orchestrationStateKeepsLifecycleAndLegacyConstraintPolicyUnavailableUnlessCallerSuppliesLifecycle() {
        Path database = tempDirectory.resolve("m16-api-state.db");
        Path fixture = http.fixture("openspec-basic");

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            String registrationBody = "{\"workspace\":" + http.jsonString(fixture.toString()) + "}";
            String projectId = http.field(http.postJson(server, "/projects", registrationBody).body(), "projectId");
            ApiTestSupport.Response sync = http.post(server, "/projects/" + projectId + "/sync");
            assertEquals(200, sync.status(), sync.body());
            String snapshotId = http.field(sync.body(), "snapshotId");
            String changeId = http.field(http.get(server, "/projects/" + projectId + "/changes").body(), "id");

            ApiTestSupport.Response unavailable = http.get(
                    server, "/projects/" + projectId + "/changes/" + changeId + "/orchestration");
            assertEquals(200, unavailable.status(), unavailable.body());
            assertTrue(unavailable.body().contains("\"source\":\"UNAVAILABLE\""), unavailable.body());
            assertTrue(unavailable.body().contains("\"state\":null"), unavailable.body());
            assertTrue(unavailable.body().contains("\"nextAllowedTransitions\":[]"), unavailable.body());
            assertTrue(unavailable.body().contains("\"acceptanceCriteria\":{\"status\":\"AVAILABLE\""), unavailable.body());
            assertTrue(unavailable.body().contains("\"blockingConstraints\":{\"status\":\"UNKNOWN\""), unavailable.body());
            assertTrue(unavailable.body().contains("\"observedCount\":0"), unavailable.body());
            assertTrue(unavailable.body().contains("blockingConstraints"), unavailable.body());
            assertTrue(!unavailable.body().contains("UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED"), unavailable.body());
            assertTrue(unavailable.body().contains("\"persisted\":false"), unavailable.body());
            assertTrue(unavailable.body().contains(snapshotId), unavailable.body());

            ApiTestSupport.Response explicit = http.get(
                    server,
                    "/projects/" + projectId + "/changes/" + changeId
                            + "/orchestration?lifecycleState=DRAFT");
            assertEquals(200, explicit.status(), explicit.body());
            assertTrue(explicit.body().contains("\"source\":\"CALLER_SUPPLIED\""), explicit.body());
            assertTrue(explicit.body().contains("\"state\":\"DRAFT\""), explicit.body());
            assertTrue(explicit.body().contains("\"nextAllowedTransitions\":[]"), explicit.body());
            assertTrue(explicit.body().contains("\"constraintEvaluations\""), explicit.body());
        }
    }

    @Test
    void transitionCheckKeepsUnknownDistinctFromBlockedAndRequiresInputWithoutMutation() {
        Path database = tempDirectory.resolve("m16-api-transition.db");
        Path fixture = http.fixture("openspec-basic");

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            String registrationBody = "{\"workspace\":" + http.jsonString(fixture.toString()) + "}";
            String projectId = http.field(http.postJson(server, "/projects", registrationBody).body(), "projectId");
            ApiTestSupport.Response sync = http.post(server, "/projects/" + projectId + "/sync");
            String snapshotId = http.field(sync.body(), "snapshotId");
            String changeId = http.field(http.get(server, "/projects/" + projectId + "/changes").body(), "id");
            String route = "/projects/" + projectId + "/changes/" + changeId + "/transition-check";

            ApiTestSupport.Response unknownPolicy = http.postJson(
                    server, route, "{\"fromState\":\"DRAFT\",\"targetState\":\"PROPOSED\"}");
            assertEquals(200, unknownPolicy.status(), unknownPolicy.body());
            assertTrue(unknownPolicy.body().contains("\"state\":\"UNKNOWN\""), unknownPolicy.body());
            assertTrue(unknownPolicy.body().contains("blockingConstraints"), unknownPolicy.body());
            assertTrue(unknownPolicy.body().contains("\"constraintEvaluations\""), unknownPolicy.body());
            assertTrue(!unknownPolicy.body().contains("BLOCKING_CONSTRAINT"), unknownPolicy.body());

            ApiTestSupport.Response unknownFacts = http.postJson(
                    server, route, "{\"fromState\":\"PROPOSED\",\"targetState\":\"SPECIFIED\"}");
            assertEquals(200, unknownFacts.status(), unknownFacts.body());
            assertTrue(unknownFacts.body().contains("\"state\":\"UNKNOWN\""), unknownFacts.body());
            assertTrue(unknownFacts.body().contains("acceptanceCriteriaDefined"), unknownFacts.body());

            ApiTestSupport.Response requiresInput = http.postJson(
                    server, route, "{\"fromState\":\"DRAFT\",\"targetState\":\"ABANDONED\"}");
            assertEquals(200, requiresInput.status(), requiresInput.body());
            assertTrue(requiresInput.body().contains("\"state\":\"REQUIRES_INPUT\""), requiresInput.body());
            assertTrue(requiresInput.body().contains("ABANDONMENT_REASON_REQUIRED"), requiresInput.body());

            ApiTestSupport.Response project = http.get(server, "/projects/" + projectId);
            assertEquals(200, project.status(), project.body());
            assertTrue(project.body().contains(snapshotId), project.body());
        }
    }
}
