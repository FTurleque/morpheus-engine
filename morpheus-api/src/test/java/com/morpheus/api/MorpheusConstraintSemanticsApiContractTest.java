package com.morpheus.api;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusConstraintSemanticsApiContractTest {
    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void orchestrationAndTransitionExposeExplicitBlockingPolicyWithEvidence() {
        Path database = tempDirectory.resolve("m16-constraint-api.db");
        Seed seed = seed(database);

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response state = http.get(
                    server,
                    "/projects/" + seed.projectId() + "/changes/" + seed.changeId()
                            + "/orchestration?lifecycleState=IMPLEMENTING");
            assertEquals(200, state.status(), state.body());
            assertTrue(state.body().contains("\"blockingConstraints\":{\"observedCount\":1"), state.body());
            assertTrue(state.body().contains("\"status\":\"AVAILABLE\""), state.body());
            assertTrue(state.body().contains("\"severity\":\"CRITICAL\""), state.body());
            assertTrue(state.body().contains("\"severity\":\"WARNING\""), state.body());
            assertTrue(state.body().contains("\"blockingMode\":\"BLOCK_WHEN_VIOLATED\""), state.body());
            assertTrue(state.body().contains("\"blockingMode\":\"NON_BLOCKING\""), state.body());
            assertTrue(state.body().contains("\"supportingEvidenceIds\""), state.body());
            assertTrue(!state.body().contains("UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED"), state.body());

            String route = "/projects/" + seed.projectId() + "/changes/" + seed.changeId() + "/transition-check";
            ApiTestSupport.Response transition = http.postJson(
                    server,
                    route,
                    "{\"fromState\":\"IMPLEMENTING\",\"targetState\":\"VERIFYING\"}");
            assertEquals(200, transition.status(), transition.body());
            assertTrue(transition.body().contains("\"state\":\"BLOCKED\""), transition.body());
            assertTrue(transition.body().contains("BLOCKING_CONSTRAINT"), transition.body());
            assertTrue(transition.body().contains("\"constraintEvaluations\""), transition.body());
            assertTrue(transition.body().contains("\"state\":\"BLOCKING\""), transition.body());
            assertTrue(transition.body().contains("explicitly blocks lifecycle state VERIFYING"), transition.body());
            assertTrue(transition.body().contains("\"sourceEvidenceId\""), transition.body());
        }
    }

    private Seed seed(Path database) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        Path fixture = http.fixture("synthetic-basic");
        try (ApiRuntime runtime = new ApiRuntime(database)) {
            var normalized = new SyntheticSpecificationContentReader()
                    .read(
                            ProviderReadRequest.all(fixture, projectId),
                            new PersistentEntityIdentityResolver(runtime.identities))
                    .content()
                    .orElseThrow();
            new ProjectSnapshotImportService(
                    runtime.snapshots,
                    runtime.requirements,
                    runtime.content,
                    runtime.traceability)
                    .publishFull(
                            normalized,
                            Optional.of("m16-api-test"),
                            Instant.parse("2026-07-26T12:30:00Z"));
            return new Seed(projectId.toString(), normalized.changes().getFirst().id().toString());
        }
    }

    private record Seed(String projectId, String changeId) {
    }
}
