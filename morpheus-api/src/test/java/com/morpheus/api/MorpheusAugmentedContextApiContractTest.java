package com.morpheus.api;

import com.morpheus.application.context.TechnicalContextBundle;
import com.morpheus.application.context.TechnicalContextItem;
import com.morpheus.application.context.TechnicalContextObservation;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextRequest;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void availableProviderReceivesBudgetSourcesConstraintsAndExplainWithoutMorpheusReranking() {
        Path database = tempDirectory.resolve("m13-pass-through.db");
        Path fixture = http.fixture("openspec-basic");
        AtomicReference<TechnicalContextRequest> captured = new AtomicReference<>();
        ExternalIntegrationStatusProvider minosDisabled = () -> new ExternalIntegrationStatus(
                "MINOS", "DISABLED", false, "MINOS disabled for M13 test", Map.of());
        TechnicalContextProvider provider = new TechnicalContextProvider() {
            @Override
            public String system() {
                return "NEXUS";
            }

            @Override
            public ExternalIntegrationStatus status() {
                return new ExternalIntegrationStatus("NEXUS", "AVAILABLE", true, "fixture", Map.of());
            }

            @Override
            public TechnicalContextObservation build(TechnicalContextRequest request) {
                captured.set(request);
                TechnicalContextBundle bundle = new TechnicalContextBundle(
                        "nexus-project-id",
                        request.options().externalProject(),
                        request.query(),
                        request.options().explain(),
                        5,
                        request.options().tokenBudget(),
                        222,
                        List.of(new TechnicalContextItem(
                                "SYMBOL",
                                "src/main/java/SessionService.java",
                                "SessionService",
                                11,
                                21,
                                "class SessionService {}",
                                0.87654321,
                                Map.of("lexical", 0.4, "structural", 0.47654321),
                                List.of("NEXUS-ranked"),
                                222,
                                false)),
                        List.of("generated/Excluded.java"),
                        Map.of("engine", "NEXUS"));
                return TechnicalContextObservation.available(status(), bundle);
            }
        };

        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                database,
                "127.0.0.1",
                0,
                new ExternalReferenceResolverRegistry(List.of()),
                minosDisabled,
                provider)) {
            String registrationBody = "{\"workspace\":" + http.jsonString(fixture.toString()) + "}";
            String projectId = http.field(http.postJson(server, "/projects", registrationBody).body(), "projectId");
            http.post(server, "/projects/" + projectId + "/sync");
            String requirementId = http.field(
                    http.get(server, "/projects/" + projectId + "/requirements?query=session").body(), "id");

            ApiTestSupport.Response response = http.postJson(
                    server,
                    "/projects/" + projectId + "/requirements/" + requirementId + "/augmented-context",
                    "{\"nexusProject\":\"technical-project\",\"tokenBudget\":3456,"
                            + "\"requestedSources\":[\"TEST\",\"SYMBOL\",\"FILE\"],"
                            + "\"constraints\":{\"language\":\"java\",\"module\":\"core\"},\"explain\":true}");

            assertEquals(200, response.status(), response.body());
            assertEquals("technical-project", captured.get().options().externalProject());
            assertEquals(3456, captured.get().options().tokenBudget());
            assertEquals(java.util.Set.of("TEST", "SYMBOL", "FILE"), captured.get().options().requestedSources());
            assertEquals(Map.of("language", "java", "module", "core"), captured.get().options().constraints());
            assertTrue(captured.get().options().explain());
            assertTrue(captured.get().query().contains("Requirement:"), captured.get().query());
            assertTrue(captured.get().query().contains("Statement:"), captured.get().query());

            assertTrue(response.body().contains("0.87654321"), response.body());
            assertTrue(response.body().contains("NEXUS-ranked"), response.body());
            assertTrue(response.body().contains("generated/Excluded.java"), response.body());
            assertTrue(response.body().contains("\"estimatedTokens\":222"), response.body());
        }
    }
}
